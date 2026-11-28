package com.tengwear.tengmusic

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tengwear.tengmusic.lan.LanManager
import com.tengwear.tengmusic.ui.AgreementScreen
import com.tengwear.tengmusic.ui.LoginGateScreen
import com.tengwear.tengmusic.ui.WearMusicApp

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 授权失败则媒体库为空 / 旧系统无法落盘接收歌曲，可后续提示 */ }

    // 使用协议同意状态（持久化，同意后不再弹出）
    private var eulaAgreed by mutableStateOf(false)
    // 是否已越过登录引导（持久化：登录或跳过都记为完成，仅一次）
    private var loginDone by mutableStateOf(false)

    /** 启动阶段：0 协议 1 登录引导 2 主界面 */
    private var stage by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        eulaAgreed = prefs.getBoolean("eula_agreed", false)
        loginDone = prefs.getBoolean("login_done", false)
        StoreAuth.init(applicationContext)

        stage = when {
            !eulaAgreed -> 0
            !loginDone -> 1
            else -> 2
        }
        if (stage == 2) startAppServices()

        setContent {
            when (stage) {
                0 -> AgreementScreen(
                    onAgree = {
                        prefs.edit().putBoolean("eula_agreed", true).apply()
                        eulaAgreed = true
                        stage = 1
                    },
                    onDecline = { finishAffinity() },
                )
                1 -> LoginGateScreen(
                    onDone = {
                        prefs.edit().putBoolean("login_done", true).apply()
                        loginDone = true
                        stage = 2
                        startAppServices()
                    },
                )
                else -> WearMusicApp(viewModel())
            }
        }

        // 冷启动回调：商店签发授权后深链直传（tengmusic://auth/callback?code=...）
        handleAuthCallback(intent)
    }

    /** 热启动回调：应用已在前台/后台时商店回跳 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    /**
     * 处理商店授权回调：校验授权码（含时间窗/设备绑定/nonce）并完成绑定。
     * 成功即推进登录流程并提示；失败给出精确原因（v2 协议诊断）。
     */
    private fun handleAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "tengmusic") return
        val email = StoreAuth.handleCallback(applicationContext, data)
        if (email != null) {
            getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit().putBoolean("login_done", true).apply()
            loginDone = true
            if (stage == 1) {
                stage = 2
                startAppServices()
            }
            Toast.makeText(this, "登录成功：$email", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                StoreAuth.diagnose(applicationContext, data.getQueryParameter("code") ?: ""),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 从商店返回时（窗口重新获焦）自动尝试读取剪贴板授权码完成绑定：
     * 无论是登录引导页还是主界面内点「需登录」跳转商店，返回后都能一次性绑定。
     * 检测到有效授权码即绑定并提示；无效时若剪贴板确实是授权码，
     * 显示一次具体失败原因（同一枚码不重复提示，v1.72）。
     */
    private var lastFailedClipboardCode: String? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || stage == 0 || !eulaAgreed) return
        if (StoreAuth.isLoggedIn(this)) return
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val code = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
            val email = StoreAuth.verifyAndBind(this, code)
            if (email != null) {
                getSharedPreferences("user_prefs", MODE_PRIVATE)
                    .edit().putBoolean("login_done", true).apply()
                loginDone = true
                if (stage == 1) {
                    stage = 2
                    startAppServices()
                }
                Toast.makeText(this, "登录成功：$email", Toast.LENGTH_SHORT).show()
            } else if (StoreAuth.looksLikeGrantCode(code) && code != lastFailedClipboardCode) {
                lastFailedClipboardCode = code
                Toast.makeText(this, StoreAuth.diagnose(this, code), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 权限申请 + 局域网服务：仅在使用协议同意后执行 */
    private fun startAppServices() {
        // 运行时申请本地音乐读取权限（按系统版本区分）；
        // API 28 及以下还需写权限才能把「传歌」收到的文件落进公共音乐目录
        val perms = when {
            Build.VERSION.SDK_INT >= 33 ->
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            Build.VERSION.SDK_INT <= 28 ->
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            else ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)

        // 局域网：开 UDP 发现 + TCP 传输服务，并清掉上次异常退出的缓存残留
        LanManager.start(applicationContext)
    }

    override fun onDestroy() {
        // 发 bye 让对端立刻知道我们下线，并关闭 socket
        LanManager.stop()
        super.onDestroy()
    }
}
