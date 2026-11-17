package com.tengwear.tengmusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tengwear.tengmusic.lan.LanManager
import com.tengwear.tengmusic.ui.AgreementScreen
import com.tengwear.tengmusic.ui.WearMusicApp

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 授权失败则媒体库为空 / 旧系统无法落盘接收歌曲，可后续提示 */ }

    // 使用协议同意状态（持久化，同意后不再弹出）
    private var eulaAgreed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eulaAgreed = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getBoolean("eula_agreed", false)

        if (eulaAgreed) {
            startAppServices()
        }

        // 纯手表应用：直接加载 Wear OS UI（未同意协议时先显示协议页）
        setContent {
            if (!eulaAgreed) {
                AgreementScreen(
                    onAgree = {
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                            .edit().putBoolean("eula_agreed", true).apply()
                        eulaAgreed = true
                        startAppServices()
                    },
                    onDecline = { finishAffinity() },
                )
            } else {
                WearMusicApp(viewModel())
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
