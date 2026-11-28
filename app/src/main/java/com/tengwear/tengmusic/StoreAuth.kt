package com.tengwear.tengmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TENC Store 授权调度链（v1.68）。
 *
 * 音乐 App 与商店应用（com.tengwear.tengstore）联动：
 *  - 协议同意后拉起商店授权页（tencstore://app/auth/TENGMUSIC），商店给本机
 *    签发一枚加密授权码并复制到剪贴板；
 *  - 本模块读取剪贴板中的授权码，用与商店相同的内置密码本离线解密校验，
 *    校验通过即视为「已登录」，本地持久化，之后离线可用、不再重复校验。
 *  - 应用「检查更新」「反馈」改为深链跳商店对应页面，由商店完成下载/发送。
 *
 * 授权码格式（商店 AuthCenterScreen.addAuthGrant 签发）：
 *   AES-GCM(password=LOG_CODEBOOK, iv=12B, tag=128bit) 加密下列明文后 Base64(NO_WRAP)：
 *     TENC-GRANT app=TENGMUSIC email=<邮箱> time=<毫秒时间戳>
 */
object StoreAuth {

    /** 商店包名与应用内深链 host */
    const val STORE_PKG = "com.tengwear.tengstore"
    private const val SCHEME = "tencstore"

    /** 请求授权时传给商店的应用名（商店会预填到授权确认页） */
    const val APP_NAME = "TENGMUSIC"

    /** 本应用在商店的仓库标识（owner~~repo，~~ 代替路由斜杠） */
    const val REPO = "lliiaannggyu~~TENGmusic"

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

    /**
     * 校验剪贴板中的授权码是否为商店签发给本 App 的有效授权。
     * 成功返回其中携带的邮箱，失败返回 null。全程离线，不联网。
     *
     * 容错：解码前剥离所有空白字符（复制粘贴可能引入换行/空格）；
     * 应用名匹配不区分大小写（商店授权中心手动输入可能大小写不一）。
     */
    fun verifyGrant(code: String): String? {
        val plain = decryptText(code.replace(Regex("\\s+"), ""), LOG_CODEBOOK) ?: return null
        // 明文：TENC-GRANT app=TENGMUSIC email=<...> time=<...>
        if (!plain.startsWith("TENC-GRANT")) return null
        if (!Regex("app=$APP_NAME", RegexOption.IGNORE_CASE).containsMatchIn(plain)) return null
        val email = Regex("email=([^\\s]+)").find(plain)?.groupValues?.get(1)
        return email?.takeIf { it.contains("@") }
    }

    /**
     * 诊断一枚授权码失败的具体原因（v1.70）：用于失败时给用户精确提示，
     * 替代笼统的「无效或过期」。本 App 不做时间校验，不存在「过期」。
     */
    fun diagnose(code: String): String {
        val clean = code.replace(Regex("\\s+"), "")
        val plain = decryptText(clean, LOG_CODEBOOK)
            ?: return "解密失败：授权码内容损坏，或商店版本与密码本不一致"
        if (!plain.startsWith("TENC-GRANT")) return "格式错误：这段内容不是 TENC-GRANT 授权码"
        val app = Regex("app=([^\\s]+)").find(plain)?.groupValues?.get(1)
        if (app == null || !app.equals(APP_NAME, ignoreCase = true)) {
            return "应用名不匹配：该授权码签发给了「${app ?: "?"}」，本应用要求「$APP_NAME」。请到商店授权时应用名填 $APP_NAME"
        }
        val email = Regex("email=([^\\s]+)").find(plain)?.groupValues?.get(1)
        if (email == null || !email.contains("@")) {
            return "邮箱缺失：商店未绑定开发者邮箱就签发了授权码"
        }
        return "OK"
    }

    /** 校验并绑定。成功返回邮箱，失败返回 null。 */
    fun verifyAndBind(context: Context, code: String): String? {
        val email = verifyGrant(code) ?: return null
        bind(context, email)
        return email
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

    /** 拉起商店授权页（请求给本 App 签发授权码） */
    fun openAuth(context: Context) = open(context, "$SCHEME://app/auth/$APP_NAME")

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
