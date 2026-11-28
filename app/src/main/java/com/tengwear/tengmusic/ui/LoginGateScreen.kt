package com.tengwear.tengmusic.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.tengwear.tengmusic.StoreAuth

/**
 * 协议同意后的登录引导页（v1.68）。
 *
 * 通过 TENC 商店一次性授权：点「打开商店授权」→ 商店签发授权码并自动复制到剪贴板
 * → 返回本 App 点「读取授权码完成登录」→ 离线解密校验 → 本地绑定，此后免登录。
 * 未提供商店 / 暂不想登录时可选「跳过」，基础播放功能照常可用，仅部分功能需登录。
 */
@Composable
fun LoginGateScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val teal = Color(0xFF4FD1C5)

    Box(Modifier.fillMaxSize().background(Color(0xFF06070A))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "登录 TENGMUSIC",
                fontSize = 15.sp,
                color = teal,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "部分功能（主题工坊、EQ 调节、听歌统计）需通过 TENC 商店授权后使用。" +
                    "只需绑定一次，之后离线可用，无需重复登录。",
                fontSize = 10.sp,
                color = Color(0x99FFFFFF),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            // ① 打开商店授权
            Button(
                onClick = {
                    StoreAuth.openAuth(ctx)
                    Toast.makeText(ctx, "已打开商店，请同意授权", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = teal,
                    contentColor = Color(0xFF06343B),
                ),
            ) {
                Text("① 打开商店授权", fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "商店会签发授权码并自动复制",
                fontSize = 8.sp,
                color = Color(0x66FFFFFF),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            // ② 读取剪贴板授权码完成绑定
            Button(
                onClick = {
                    val code = readClipboard(ctx)
                    if (code.isNullOrBlank()) {
                        Toast.makeText(ctx, "剪贴板没有授权码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val email = StoreAuth.verifyAndBind(ctx, code)
                    if (email != null) {
                        Toast.makeText(ctx, "登录成功：$email", Toast.LENGTH_SHORT).show()
                        onDone()
                    } else {
                        // 显示具体失败原因（v1.72），不再笼统提示「无效」
                        Toast.makeText(ctx, StoreAuth.diagnose(ctx, code), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2D36),
                    contentColor = Color.White,
                ),
            ) {
                Text("② 读取授权码完成登录", fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))

            // ③ 跳过（离线体验）
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1A2A2D36),
                    contentColor = Color(0x99FFFFFF),
                ),
            ) {
                Text("暂不登录，先体验", fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "跳过后仍可听歌，仅部分功能锁定；可随时到 设置 → 账号与授权 登录",
                fontSize = 8.sp,
                color = Color(0x66FFFFFF),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 读取系统剪贴板纯文本（按钮点击时窗口已获焦点，满足 Android 10+ 读取限制） */
private fun readClipboard(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.getItemAt(0)?.text?.toString()
}.getOrNull()
