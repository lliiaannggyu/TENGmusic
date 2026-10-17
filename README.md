# TENGmusic

一个 **Wear OS 本地音乐播放器**，单个 APK 同时支持圆形手表与安卓手机。专辑选曲页还原了经典 iPod 的 **Cover Flow** 交互。

## 特性

- **纯 Wear OS 手表应用**：圆形表盘布局，内容约束在安全圆内；单 APK 亦可装到手机但按手表 UI 呈现
- **M3E（Material 3 Expressive）UI**：全部使用 Wear Compose Material3 官方组件（ScalingLazyColumn / Card / SwitchButton / RadioButton / Slider / CompactButton），无自绘控件
- **iPod Cover Flow**：正方形密集卡片走廊、3D 侧转、等比缩放、镜像倒影、惯性滑动 + 居中回弹；表冠每咔哒一格
- **表冠（rotary）全链路支持**：播放页音量、歌词滚动、专辑流翻页、列表滚动、EQ 频段微调
- **歌词系统**：LRC 解析、点击跳播、8 种切换动画可选（设置内带实时预览）、逐字填充（多行歌词安全）、字号 12–22sp 可调、两行小字占位（避免单/双行切换时布局跳动）
- **均衡器**：移植自 AndroidEqualizer（EqualizerManager：setup/release/量程/预设/回调）+ 竖条频段手势调节 + 系统设备预设一键套用 + 频率响应曲线 + 表冠微调
- **归类（智能列表）**：全部歌曲 / 收藏 / 自定义类别；点击=播放该归类；双击歌名进入归类模式（点类别=把当前歌加入/移出）；长按 700ms 松手弹删除；长按后拖动排序（跟手 + 目标高亮 + 松手才交换，无跳屏）；新增类别按钮跟随列表滚动
- **封面工具**：单曲写入 + 批量模式（一次多选 30 张图，按「歌名/文件名与图片名最长公共子串 ≥3 字符」自动匹配，匹配结果逐条可删、确认后批量写入）
- **播放器外观自定义**：进度环半径（5%~50%）/ 粗细、背景模糊 / 明度、歌词颜色（跟随封面 / M3 官方 tonal palette 17 色阶 / 直接填十六进制色号）；设置页 Card / RadioButton / SwitchButton / Slider 均跟随强调色
- `minSdk 25`，Kotlin + Jetpack Compose

## 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 2.x（Compose Compiler 插件） |
| Compose BOM | 2026.06.01（Compose 1.11.4 / material3 1.4.0） |
| Wear Compose（material3 / foundation） | 1.6.2 |
| Media3 | 1.3.1 |
| Coil | 2.6.0 |
| minSdk / targetSdk / compileSdk | 25 / 36 / 36 |

## 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## Cover Flow 说明

专辑流基于开源库 **[compose-coverflow](https://github.com/pakohan/compose-coverflow)**（MIT，Patrick Kohan）移植而来，位于 `com/tengwear/tengmusic/coverflow/`：

- 几何/倒影算法原样保留
- 为适配 Compose 1.6.7 做了最小改动（去掉 `@Parcelize`、`Dp` 换算、自定义回弹 fling）

可调参数（`CoverFlowParams`）：

| 参数 | 含义 | 当前值 |
|---|---|---|
| `size` | 中间封面边长占容器短边比例 | `0.5` |
| `offset` | 相邻封面间距（越小越密集） | `0.4` |
| `angle` | 最大侧转角 | `55f` |
| `shift` | 侧卡水平位移（让中间更突出） | `0.4` |
| `zoom` | 侧卡缩小比例 | `0.8` |
| `mirror` | 镜像倒影 | `true` |

## 许可

**AGPL-3.0**，详见 [LICENSE](LICENSE)。

> 本项目整体以 GNU Affero General Public License v3.0 发布。其中内嵌移植的 **compose-coverflow**（位于 `com/tengwear/tengmusic/coverflow/`）仍保留其原始 **MIT** 许可，相关著作权归属于 Patrick Kohan，按 MIT 条款使用。

## 致谢（内嵌移植的开源项目）

| 模块 | 来源 | 许可 |
|---|---|---|
| Cover Flow（`coverflow/`） | [pakohan/compose-coverflow](https://github.com/pakohan/compose-coverflow) | MIT |
| 均衡器（`AudioEffects.kt` 的 `EqualizerManager`） | [bullheadandplato/AndroidEqualizer](https://github.com/bullheadandplato/AndroidEqualizer)（252★，竖条频段交互形态） | Apache-2.0 |