package com.example.wearmusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wearmusic.ui.WearMusicApp

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授权失败则媒体库为空，可后续提示 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 运行时申请本地音乐读取权限（按系统版本区分）
        when {
            Build.VERSION.SDK_INT >= 33 ->
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            else ->
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 纯手表应用：直接加载 Wear OS UI
        setContent {
            WearMusicApp(viewModel())
        }
    }
}
