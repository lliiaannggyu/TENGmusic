package com.tengwear.tengmusic.ui

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text

/**
 * 首次启动的使用协议页（v1.46）。
 *
 * 规则：必须把协议内容**滚动到底**，「同意并继续」按钮才会解锁；
 * 「不同意」则退出应用。同意状态由 MainActivity 持久化，此后不再弹出。
 */
@Composable
fun AgreementScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    val scroll = rememberScrollState()
    // 滚动到底判定：已滚到最大值（内容不满一屏时 maxValue=0，同样视为已读完）
    val reachedBottom by remember {
        derivedStateOf { scroll.value >= scroll.maxValue || scroll.maxValue == 0 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "TENGMUSIC 使用协议",
                fontSize = 14.sp,
                color = Color(0xFF4FD1C5),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "请在使用前仔细阅读以下条款。继续向下滚动至文末，方可点击同意。",
                fontSize = 10.sp,
                color = Color(0x99FFFFFF),
            )
            Spacer(Modifier.height(12.dp))

            AgreementSection("一、软件性质") {
                "TENGMUSIC 是一款运行于 Wear OS 手表的本地音乐播放工具，仅用于播放您设备中已合法持有的音频文件。本应用本身不内置、不提供、不推荐任何音乐资源，所有曲目均来自您自己的本地曲库。"
            }
            AgreementSection("二、在线功能与第三方服务") {
                "应用内的「在线搜索 / 音源」功能不连接任何官方或默认服务器，仅调用您自行配置、自行填写的第三方接口地址。该接口所返回内容的合法性、安全性及可用性均由对应的第三方服务提供者负责，与开发者无关。您应确保仅检索和下载获得合法授权的内容；因使用第三方接口产生的一切版权纠纷与法律责任，由您自行承担。"
            }
            AgreementSection("三、隐私与数据") {
                "本应用不注册、不收集、不上传您的任何个人数据。播放记录、收藏、设置等信息均只存储在您的手表本地。首次使用可选择通过 TENC 商店进行一次性授权：商店在本机签发授权码，应用离线校验后仅把绑定结果存在本地，不联网、不上传；应用更新与反馈通过深链跳转商店完成。"
            }
            AgreementSection("四、免责声明") {
                "本应用按「现状」提供，开发者不对适用性、无错误或不中断作出担保。因使用本应用导致的任何数据丢失、设备异常、听力损伤（请合理控制音量）或第三方权利纠纷，开发者不承担责任。"
            }
            AgreementSection("五、使用范围") {
                "本应用仅供个人学习、交流与非商业用途使用。未经许可，不得将本应用或其修改版用于商业分发，不得利用本应用从事任何违反法律法规或侵犯他人合法权益的活动。"
            }
            AgreementSection("六、开源与致谢") {
                "本应用基于 Android 开源生态构建，使用了 Compose、Media3、Coil 等开源组件，完整致谢清单见 设置 → 关于 → 用到的开源项目。"
            }

            Spacer(Modifier.height(14.dp))
            Text(
                if (reachedBottom) "—— 已阅读至文末 ——" else "↓ 继续向下滚动阅读全部条款 ↓",
                fontSize = 9.sp,
                color = if (reachedBottom) Color(0xFF4FD1C5) else Color(0x66FFFFFF),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onAgree,
                enabled = reachedBottom,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (reachedBottom) Color(0xFF4FD1C5) else Color(0xFF2A2D36),
                    contentColor = if (reachedBottom) Color(0xFF06343B) else Color(0x66FFFFFF),
                ),
            ) {
                Text(if (reachedBottom) "同意并继续" else "滚动到底后可同意", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDecline,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2D36),
                    contentColor = Color(0x99FFFFFF),
                ),
            ) {
                Text("不同意并退出", fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AgreementSection(title: String, body: () -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Color(0x141B2A35), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(title, fontSize = 11.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(body(), fontSize = 9.sp, color = Color(0xB3FFFFFF), lineHeight = 13.sp)
    }
}
