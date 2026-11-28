package com.tengwear.tengmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TENC Store 通用授权协议 v2（v1.71）。
 *
 * 授权链路（免剪贴板直传）：
 *  1. 本应用生成一次性 nonce + 设备指纹，深链拉起商店授权中心：
 *     tencstore://app/auth/TENGMUSIC?n={nonce}&d={device}&cb={回调URI(URL编码)}
 *     （这套请求协议对任何应用通用，应用名/回调均由发起方声明）
 *  2. 商店确认后签发授权码（明文含 app/email/time/n/d），深链回调
 *     tengmusic://auth/callback?code={授权码} 直传回来；
 *  3. 本地校验：AES-GCM 解密 + 应用名匹配 + 时间窗(30分钟) + 设备绑定 +
 *     nonce 防重放，通过即本地一次绑定，离线可用。
 *  剪贴板读取与手动输入保留为兜底路径。
 *
 * 授权码明文格式（商店 addAuthGrant 签发）：
 *   TENC-GRANT app={应用名} email={邮箱} time={毫秒}[ n={nonce}][ d={device}]
 */
object StoreAuth {

    /** 商店包名与应用内深链 host */
    const val STORE_PKG = "com.tengwear.tengstore"
    private const val SCHEME = "tencstore"

    /** 请求授权时传给商店的应用名（商店会锁定显示，不允许改） */
    const val APP_NAME = "TENGMUSIC"

    /** 本应用在商店的仓库标识（owner~~repo，~~ 代替路由斜杠） */
    const val REPO = "lliiaannggyu~~TENGmusic"

    /** 授权回调 URI：商店签发后深链回跳（本应用 Manifest 已注册） */
    const val CB_URI = "tengmusic://auth/callback"

    /** 授权码有效期（签发时间距校验时间）：30 分钟 */
    private const val GRANT_WINDOW_MS = 30 * 60 * 1000L

    /** 发起的授权请求 nonce 有效期：10 分钟 */
    private const val NONCE_WINDOW_MS = 10 * 60 * 1000L

    /**
     * 授权密码本：必须与签发授权的商店 APK 内 BuildConfig.LOG_CODEBOOK 完全一致。
     * 该值从商店 v1.63 发布包中提取（stores 内唯一 CODEBOOK 常量）。
     */
    private const val LOG_CODEBOOK = "#TENC::CODEBOOK::2026::c83b21e7::log"

    // ---------------- 本地「一次绑定」持久化 ----------------
    private const val PREF = "tengmusic_store_auth"
    private const val KEY_BOUND = "bound"
    private const val KEY_EMAIL = "email"
    private const val KEY_BOUND_AT = "bound_at"

    // 登录状态流：仅反映本地绑定，绝不发起任何联网校验（满足「离线可用」）。
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** App 启动时把本地绑定状态灌入状态流（一次，之后常驻内存） */
    fun init(context: Context) {
        _loggedIn.value = isLoggedIn(context)
    }

    /** 是否已登录（一次绑定后常驻，离线可用） */
    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_BOUND, false)

    /** 已绑定邮箱（用于设置页展示） */
    fun boundEmail(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)

    /** 写入绑定结果（校验通过后调用） */
    private fun bind(context: Context, email: String?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BOUND, true)
            .putString(KEY_EMAIL, email)
            .putLong(KEY_BOUND_AT, System.currentTimeMillis())
            .apply()
        _loggedIn.value = true
    }

    /** 登出（清除绑定，设置页「授权管理」用） */
    fun logout(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BOUND, false)
            .remove(KEY_EMAIL)
            .remove(KEY_BOUND_AT)
            .apply()
        _loggedIn.value = false
    }

    // ---------------- 授权码校验（离线 AES-GCM 解密） ----------------

    // 待匹配的授权请求 nonce（发起授权时写入，校验通过或过期后清除）
    private const val KEY_PENDING_NONCE = "pending_nonce"
    private const val KEY_PENDING_AT = "pending_at"

    /** 设备指纹：ANDROID_ID（同设备稳定，作为授权码设备绑定凭据） */
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    /**
     * 发起通用授权请求（v2 链路）：生成一次性 nonce + 设备指纹，深链拉起商店
     * 授权中心并携带回调 URI（cb 由发起方声明，商店对任何应用通用）。
     * 商店确认签发后会直接深链回跳本应用完成绑定，不再依赖剪贴板。
     */
    fun requestAuth(context: Context) {
        val nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_NONCE, nonce)
            .putLong(KEY_PENDING_AT, System.currentTimeMillis())
            .apply()
        val uri = "$SCHEME://app/auth/$APP_NAME" +
            "?n=$nonce" +
            "&d=${Uri.encode(deviceId(context))}" +
            "&cb=${Uri.encode(CB_URI)}"
        open(context, uri)
    }

    /**
     * 处理商店签发后的回调：tengmusic://auth/callback?code={授权码}。
     * 成功返回邮箱并完成绑定，失败返回 null（用 diagnose 看原因）。
     */
    fun handleCallback(context: Context, data: Uri?): String? {
        if (data?.scheme != "tengmusic") return null
        val code = data.getQueryParameter("code") ?: return null
        return verifyAndBind(context, code)
    }

    /** 校验并绑定。成功返回邮箱，失败返回 null（diagnose 可查具体原因）。 */
    fun verifyAndBind(context: Context, code: String): String? {
        val email = verifyGrant(context, code) ?: return null
        bind(context, email)
        return email
    }

    /**
     * 剪贴板内容是否「看起来是一枚授权码」：能解密且明文是 TENC-GRANT 开头。
     * 用于自动绑定失败时决定是否提示（避免把无关剪贴板内容当授权码报错）。
     */
    fun looksLikeGrantCode(code: String): Boolean {
        val plain = decryptText(code.replace(Regex("\\s+"), ""), LOG_CODEBOOK)
        return plain?.startsWith("TENC-GRANT") == true
    }

    /**
     * 授权码校验规则（离线，不联网）：
     *  - 解密前剥离所有空白（复制粘贴容错）；应用名匹配不区分大小写；
     *  - time 距今 ≤ 30 分钟（有效期，防旧码流通）；
     *  - 明文带 d= 时必须与本机设备指纹一致（授权码不可跨设备）；
     *  - 明文带 n= 时必须与本应用发起的请求 nonce 一致且未过期（防重放）；
     *  - 明文缺 n/d/time 字段时放行（兼容旧版商店签发的码）。
     */
    private fun verifyGrant(context: Context, code: String): String? {
        val plain = decryptText(code.replace(Regex("\\s+"), ""), LOG_CODEBOOK) ?: return null
        if (!plain.startsWith("TENC-GRANT")) return null
        if (!Regex("app=$APP_NAME", RegexOption.IGNORE_CASE).containsMatchIn(plain)) return null
        // 有效期：签发时间距现在 ≤ 30 分钟（无 time 字段放行，兼容旧商店）
        Regex("time=(-?\\d+)").find(plain)?.groupValues?.get(1)?.toLongOrNull()?.let { ts ->
            if (kotlin.math.abs(System.currentTimeMillis() - ts) > GRANT_WINDOW_MS) return null
        }
        // 设备绑定：码内 d 必须与本机一致（无 d 字段放行，兼容旧商店）
        Regex("d=([^\\s]+)").find(plain)?.groupValues?.get(1)?.let { dev ->
            if (dev != deviceId(context)) return null
        }
        // 防重放：仅当本应用发起过授权请求（存在 pending nonce）时才校验；
        // 校验通过才消费（一次性）；失败不清空，避免一次意外失败烧掉 nonce
        // 导致之后同一枚有效码永远校验不过（v1.72 修复）。
        Regex("n=([^\\s]+)").find(plain)?.groupValues?.get(1)?.let { n ->
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val pending = prefs.getString(KEY_PENDING_NONCE, null)
            if (pending != null) {
                val at = prefs.getLong(KEY_PENDING_AT, 0L)
                val valid = pending == n && System.currentTimeMillis() - at <= NONCE_WINDOW_MS
                if (!valid) return null
                prefs.edit().remove(KEY_PENDING_NONCE).remove(KEY_PENDING_AT).apply()
            }
        }
        val email = Regex("email=([^\\s]+)").find(plain)?.groupValues?.get(1)
        return email?.takeIf { it.contains("@") }
    }

    /**
     * 诊断一枚授权码失败的具体原因：用于失败时给用户精确提示。
     */
    fun diagnose(context: Context, code: String): String {
        val clean = code.replace(Regex("\\s+"), "")
        val plain = decryptText(clean, LOG_CODEBOOK)
            ?: return "解密失败：授权码内容损坏，或商店版本与密码本不一致"
        if (!plain.startsWith("TENC-GRANT")) return "格式错误：这段内容不是 TENC-GRANT 授权码"
        val app = Regex("app=([^\\s]+)").find(plain)?.groupValues?.get(1)
        if (app == null || !app.equals(APP_NAME, ignoreCase = true)) {
            return "应用名不匹配：该授权码签发给了「${app ?: "?"}」，本应用要求「$APP_NAME」"
        }
        Regex("time=(-?\\d+)").find(plain)?.groupValues?.get(1)?.toLongOrNull()?.let { ts ->
            if (kotlin.math.abs(System.currentTimeMillis() - ts) > GRANT_WINDOW_MS) {
                return "授权码已过期（超过30分钟）：请从本应用重新发起授权"
            }
        }
        Regex("d=([^\\s]+)").find(plain)?.groupValues?.get(1)?.let { dev ->
            if (dev != deviceId(context)) {
                return "设备不匹配：该授权码签发给了其他设备，授权码不可跨设备使用"
            }
        }
        Regex("n=([^\\s]+)").find(plain)?.groupValues?.get(1)?.let { n ->
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val pending = prefs.getString(KEY_PENDING_NONCE, null)
            // 未发起过授权请求时不判 nonce 失败（兼容从别处获得的合法码）
            if (pending != null) {
                val at = prefs.getLong(KEY_PENDING_AT, 0L)
                if (pending != n || System.currentTimeMillis() - at > NONCE_WINDOW_MS) {
                    return "授权请求不符：该码与本应用当前待处理的授权请求不一致" +
                        "（可能已消费或超时），请从本应用重新发起授权"
                }
            }
        }
        val email = Regex("email=([^\\s]+)").find(plain)?.groupValues?.get(1)
        if (email == null || !email.contains("@")) {
            return "邮箱缺失：商店未绑定开发者邮箱就签发了授权码"
        }
        return "OK"
    }

    private fun decryptText(token: String, password: String): String? = runCatching {
        val data = Base64.decode(token.trim(), Base64.NO_WRAP)
        if (data.size <= 12) return null
        val iv = data.copyOfRange(0, 12)
        val body = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyOf(password), GCMParameterSpec(128, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    private fun keyOf(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    // ---------------- 深链跳转 ----------------

    /** 拉起商店授权页：走 v2 通用协议（nonce+设备指纹+回调直传），兼容旧调用点 */
    fun openAuth(context: Context) = requestAuth(context)

    /** 跳商店里本 App 的更新/下载页 */
    fun openUpdate(context: Context) = open(context, "$SCHEME://app/update/$REPO")

    /** 跳商店里本 App 的反馈页 */
    fun openFeedback(context: Context) = open(context, "$SCHEME://app/feedback/$REPO")

    private fun open(context: Context, uri: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // 商店未安装等：调用方用 Toast 提示
        }
    }
}
