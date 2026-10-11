package com.example.wearmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wearmusic.ui.PhoneApp
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

        // 运行时判断设备类型：手表用圆形 Wear UI，手机用 Material3 UI
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

        setContent {
            val vm: PlayerViewModel = viewModel()
            if (isWatch) WearMusicApp(vm) else PhoneApp(vm)
        }
    }
}
