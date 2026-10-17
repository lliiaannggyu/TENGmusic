package com.tengwear.tengmusic.ui

/**
 * 应用内开源致谢清单。
 *
 * 本应用使用了以下开源项目/开源代码（许可证均为 Apache-2.0 或 MIT，
 * 与本项目采用的 AGPL-3.0 兼容）。完整许可证文本见各项目仓库。
 */
data class OpenSourceProject(
    val name: String,
    val license: String,
    val url: String,
)

object OpenSourceProjects {

    val ALL: List<OpenSourceProject> = listOf(
        OpenSourceProject(
            name = "AndroidX Wear Compose（compose-material3 / compose-foundation 1.6.2）",
            license = "Apache-2.0",
            url = "https://android.googlesource.com/platform/frameworks/support/",
        ),
        OpenSourceProject(
            name = "Jetpack Compose（ui / foundation / material-icons-extended）",
            license = "Apache-2.0",
            url = "https://android.googlesource.com/platform/frameworks/support/",
        ),
        OpenSourceProject(
            name = "AndroidX Media3（media3-exoplayer / media3-session / media3-common）",
            license = "Apache-2.0",
            url = "https://github.com/androidx/media",
        ),
        OpenSourceProject(
            name = "AndroidX Core KTX / Lifecycle / Activity Compose",
            license = "Apache-2.0",
            url = "https://android.googlesource.com/platform/frameworks/support/",
        ),
        OpenSourceProject(
            name = "Coil（coil-compose 2.6.0，专辑封面加载）",
            license = "Apache-2.0",
            url = "https://github.com/coil-kt/coil",
        ),
        OpenSourceProject(
            name = "compose-coverflow（pakohan，Cover Flow 封面流几何/倒影实现，MIT，本仓库已移植改造）",
            license = "MIT",
            url = "https://github.com/pakohan/compose-coverflow",
        ),
        OpenSourceProject(
            name = "Horologist（Google 出品的 Wear OS 开源组件库，本项目的表冠/滚动交互参考其 rotaryScrollable 用法）",
            license = "Apache-2.0",
            url = "https://github.com/google/horologist",
        ),
        OpenSourceProject(
            name = "Equalizer314（bearinmindcat，490★，Kotlin 参数均衡器，本项目均衡器布局与调节形态的设计参考）",
            license = "GPL-3.0",
            url = "https://github.com/bearinmindcat/Equalizer314",
        ),
        OpenSourceProject(
            name = "AndroidEqualizer（bullheadandplato，252★，Equalizer View + 频段管理，本项目均衡器频段交互的设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/bullheadandplato/AndroidEqualizer",
        ),
        OpenSourceProject(
            name = "markzhai/LyricView（268★，Apache-2.0，歌词滚动动画设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/markzhai/LyricView",
        ),
        OpenSourceProject(
            name = "wangchenyan/lrcview（716★，可拖动歌词控件，歌词切换动画设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/wangchenyan/lrcview",
        ),
        OpenSourceProject(
            name = "douzifly/android-lrc-view（164★，歌词 seek 与切换交互设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/douzifly/android-lrc-view",
        ),
        OpenSourceProject(
            name = "LyricViewDemo（asr-pub，687★，逐字高亮歌词效果设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/asr-pub/LyricViewDemo",
        ),
        OpenSourceProject(
            name = "Lyrico（Replica0110，1259★，Apache-2.0，逐字歌词设计参考）",
            license = "Apache-2.0",
            url = "https://github.com/Replica0110/Lyrico",
        ),
    )
}
