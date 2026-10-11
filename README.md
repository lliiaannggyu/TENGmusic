# TENGmusic

一个 **Wear OS 本地音乐播放器**，单个 APK 同时支持圆形手表与安卓手机。专辑选曲页还原了经典 iPod 的 **Cover Flow** 交互。

## 特性

- **单 APK 双端**：运行时识别设备类型（`FEATURE_WATCH`），手表与手机各自呈现对应 UI
- **iPod Cover Flow**：正方形密集卡片走廊、3D 侧转、等比缩放、镜像倒影、惯性滑动 + 居中回弹
- **带回弹的吸附动画**：自写 `FlingBehavior`，按速度决定跨越张数，用带 overshoot 的弹簧回正，避免急停
- 本地音乐：MediaStore 扫描、按专辑分组、Media3 播放
- 手表端：圆形屏布局、表冠（rotary）翻页、EQ、播放速度、定时
- 手机端：Material3 三页（播放器 / 专辑流 / 队列）
- `minSdk 25`，Kotlin + Jetpack Compose

## 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 1.9.24 |
| Compose UI / Foundation | 1.6.7 |
| Wear Compose | 1.3.1 |
| Material3 | 1.2.1 |
| Media3 | — |
| Coil | — |
| minSdk / targetSdk | 25 / 34 |

## 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## Cover Flow 说明

专辑流基于开源库 **[compose-coverflow](https://github.com/pakohan/compose-coverflow)**（MIT，Patrick Kohan）移植而来，位于 `com/example/wearmusic/coverflow/`：

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

MIT，详见 [LICENSE](LICENSE)。内含对 pakohan/compose-coverflow 的第三方声明。