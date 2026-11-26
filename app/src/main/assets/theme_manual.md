# TENGMUSIC 声明式皮肤制作手册（v2 · v1.50+）

你可以用一份 **JSON 文件**重新定义播放器和歌词页的**每一个控件**：位置、大小、形状、颜色、字体、显隐、出现动画。换一份文件就像把界面**重做一遍**——这就是「声明式皮肤引擎」。

> 老版本（v1）的精简主题仍然兼容，但要实现「重做 UI」必须写 v2 皮肤：顶层加 `"version": 2`。

---

## 一、3 步上手

1. 用文本编辑器写一个纯文本文件，另存为 `名字.json`（UTF-8，无 BOM）。
2. 放到手表能访问的任意目录（如内部存储 `Download/`）。
3. 手表上 TENGMUSIC → 设置 → **主题工坊** → 选「播放器UI」或「歌词」 → **导入主题文件** → 文件管理器里勾选 `.json` → 回到列表点亮该卡片即应用。

内置文件管理器可自由浏览任意目录、进子文件夹、返回上级，无需存储权限。应用后可随时**切换 / 删除 / 恢复默认**。

---

## 二、坐标系（重要）

屏幕是一块**圆形表盘**。皮肤坐标基于一个「内容正方形」：

- `x`、`y`：控件**中心点**的相对位置，取值 `0.0 ~ 1.0`。`0,0` 是正方形左上角，`0.5,0.5` 是正中心，`1,1` 是右下角。
- 控件在该点**居中**放置。
- **安全区**：圆形四角会被裁切，重要内容尽量放在 `x/y ∈ [0.15, 0.85]`；靠边（如 `y=0.95`）只适合小图标。

```
   (0,0) ┌─────────┐ (1,0)
         │  ╭───╮  │      圆形可视区
         │  │ · │  │      中心 = (0.5,0.5)
         │  ╰───╯  │
   (0,1) └─────────┘ (1,1)
```

---

## 三、播放器皮肤（`kind = "player"`）

### 3.1 骨架

```json
{
  "version": 2,
  "kind": "player",
  "name": "我的播放器",
  "bg": "#0A0E1A",
  "bgBlur": 16,
  "dim": 0.5,
  "widgets": [ ... ]
}
```

| 顶层字段 | 类型 | 说明 | 默认 |
| --- | --- | --- | --- |
| `version` | 整数 | 必须为 `2` | 1 |
| `kind` | 字符串 | `"player"` | 必填 |
| `name` | 字符串 | 主题名（列表里显示） | 文件名 |
| `bg` | 颜色 | 背景底色（封面加载前/透明处） | 黑 |
| `bgBlur` | 数字 | 背景封面模糊半径 dp（0~60） | 16 |
| `dim` | 数字 | 背景暗化程度 0~1 | 0.55 |
| `widgets` | 数组 | 控件列表，**按顺序绘制 & 依次入场** | 空 |

### 3.2 控件 id（内置支持）

| id | 含义 | 可点击行为 |
| --- | --- | --- |
| `cover` | 专辑封面 | — |
| `title` | 歌名文本 | — |
| `artist` | 歌手文本 | — |
| `progress` | 进度环（`style=ring` 圆环 / `style=wave` 波浪盘） | — |
| `play` | 播放/暂停键 | 播放/暂停 |
| `prev` | 上一首键 | 短按切歌，长按快退 |
| `next` | 下一首键 | 短按切歌，长按快进 |
| `volume` | 音量键 | 打开音量调节浮层 |
| `mode` | 播放模式键 | 切换顺序/随机/单曲 |
| `library` | 归类键 | 保留入口（手势触发归类列表） |

不想要某个控件，就不把它写进 `widgets`（或从数组里删掉）——等于把它从界面上拿掉。想放两个同类控件也可以写两次。

### 3.3 控件字段

每个控件是一个对象，字段全部可选：

| 字段 | 类型 | 说明 | 默认（按 id 略有不同） |
| --- | --- | --- | --- |
| `id` | 字符串 | **必填**，见上表 | — |
| `show` | 布尔 | 是否显示 | `true` |
| `x` / `y` | 数字 | 中心位置 0~1 | 按 id 预设 |
| `w` / `h` | 数字 | 尺寸 dp（文字 w 无意义可省） | 按 id 预设 |
| `frac` | 数字 | 尺寸=正方形边长×该比例（cover/progress 常用，如 `0.9`=直径九成） | cover .46 / progress .90 |
| `shape` | 字符串 | `circle` / `rounded` / `rect` / `none` | 按 id 预设 |
| `radius` | 数字 | `rounded` 的圆角比例 0~1 | 0.28 |
| `bg` | 颜色 | 背景/容器色 | id 预设 |
| `color` | 颜色 | 前景色：文字色 / 图标色 / 进度色 | 白 / 主题色 |
| `borderW` | 数字 | 边框宽度 dp | 0 |
| `borderColor` | 颜色 | 边框色 | 白 |
| `fontSize` | 整数 | 字号 sp（title/artist） | title 16 / artist 11 |
| `bold` | 布尔 | 粗体（title/artist） | title=true |
| `align` | 字符串 | 文字对齐 `left`/`center`/`right` | center |
| `icon` | 字符串 | 覆盖图标名（见下） | 按 id |
| `opacity` | 数字 | 不透明度 0~1 | 1 |
| `rotate` | 数字 | 旋转角度 `-360~360` | 0 |
| `anim` | 对象 | 出现动画（见 3.5） | fade 320ms |
| **progress 专用** | | | |
| `style` | 字符串 | `ring`（圆环）/ `wave`（波浪盘） | ring |
| `stroke` | 数字 | 进度线宽 dp | 6 |
| `track` | 颜色 | 轨道底色 | 半透明白 |
| `disc` | 颜色 | 波浪盘实心色（仅 wave） | 同 color |
| `waveAmp` | 数字 | 波浪起伏幅度 0~0.3 | 0.048 |
| `waveLobes` | 整数 | 波瓣数 3~30 | 10 |
| `waveGap` | 数字 | 盘与轨道的缝隙 0~0.3 | 0.072 |
| `waveSpeed` | 整数 | 转一圈毫秒 1000~120000 | 16000 |

**图标名**（`icon` 字段可选值）：`play` `pause` `prev` `next` `volume` `mode` `shuffle` `library`。不填则用 id 对应的默认图标；`play` 会自动播/停切换。

### 3.4 颜色格式

`#RRGGBB` 或 `#AARRGGBB`（如 `#34C7F4`、`#8034C7F4`），可以不带 `#`。

### 3.5 出现动画 `anim`

控件按 `widgets` 顺序**依次入场**（每个错开 45ms），让界面像「搭起来」一样。

```json
"anim": { "type": "slideUp", "dur": 400, "delay": 0, "distance": 20, "fromScale": 0.5, "easing": "easeOut" }
```

| 字段 | 类型 | 说明 | 默认 |
| --- | --- | --- | --- |
| `type` | 字符串 | 动画类型（见下） | fade |
| `dur` | 整数 | 时长 ms（0~5000） | 320 |
| `delay` | 整数 | 额外延迟 ms | 0 |
| `distance` | 数字 | 滑动距离 dp（slide* 用） | 16 |
| `fromScale` | 数字 | 缩放起点（scale/pop 用） | 0.6 |
| `easing` | 字符串 | `linear`/`easeIn`/`easeOut`/`easeInOut`/`spring` | easeOut |

**动画类型**：`none`（无）、`fade`（淡入）、`slideUp`（从下滑入）、`slideDown`（从上滑入）、`slideLeft`（从左滑入）、`slideRight`（从右滑入）、`scale`（缩放进入）、`pop`（弹出）、`flip`（3D 翻入）、`blur`（由模糊变清晰）。

---

## 四、歌词皮肤（`kind = "lyrics"`）

### 4.1 骨架

```json
{
  "version": 2,
  "kind": "lyrics",
  "name": "我的歌词",
  "align": "center",
  "lineGap": 18,
  "padding": 10,
  "bgColor": "#000000",
  "bgGlow": "#34C7F4",
  "bgGlowStrength": 0.12,
  "active":   { "color": "#34C7F4", "size": 18, "bold": true, "scale": 1.15, "opacity": 1, "glow": true, "glowColor": "#34C7F4" },
  "inactive": { "color": "#888888", "size": 14, "bold": false, "scale": 1.0, "opacity": 0.55 },
  "enter": { "type": "slideUp", "dur": 320, "distance": 18, "easing": "easeOut" },
  "exit":  { "type": "fade", "dur": 220 },
  "karaoke": { "fill": "#34C7F4", "rest": "#666666" }
}
```

| 顶层字段 | 类型 | 说明 | 默认 |
| --- | --- | --- | --- |
| `align` | 字符串 | 整页对齐 `left`/`center`/`right` | center |
| `lineGap` | 数字 | 行间距 dp（0~80） | 18 |
| `padding` | 数字 | 左右内边距 dp | 10 |
| `bgColor` | 颜色 | 背景色 | 黑 |
| `bgGlow` | 颜色 | 背景径向弱光色 | 无 |
| `bgGlowStrength` | 数字 | 弱光强度 0~1 | 0.12 |
| `active` | 对象 | **当前行**样式（见 4.2） | — |
| `inactive` | 对象 | **非当前行**样式 | — |
| `enter` | 对象 | 成为当前行时的**进入动画**（见 4.3） | slideUp 320 |
| `exit` | 对象 | 离开当前行时的**消失动画** | fade 220 |
| `karaoke` | 对象 | 逐字效果配色（见 4.4） | — |

### 4.2 行样式 `active` / `inactive`

| 字段 | 类型 | 说明 | 默认 |
| --- | --- | --- | --- |
| `color` | 颜色 | 文字颜色 | 白（active）/ 灰（inactive） |
| `size` | 整数 | 字号 sp（8~40） | 18 / 14 |
| `bold` | 布尔 | 粗体 | true / false |
| `scale` | 数字 | 缩放 0.3~3（当前行放大就用 >1） | 1.15 / 1.0 |
| `opacity` | 数字 | 不透明度 0~1 | 1 / 0.55 |
| `glow` | 布尔 | 发光阴影 | false |
| `glowColor` | 颜色 | 阴影色 | 无 |

### 4.3 歌词动画 `enter` / `exit`

字段同 3.5 的 `anim`（`type`/`dur`/`delay`/`distance`/`fromScale`/`easing`）。

- 普通动画类型：`none` `fade` `slideUp` `slideDown` `slideLeft` `slideRight` `scale` `pop` `flip` `blur`。
- **逐字特效**：把 `enter.type` 设为 `karaoke`（按播放进度逐字点亮，配色取 `karaoke.fill`/`rest`）或 `typewriter`（随进度逐字打出）。

> 想让当前行「从下方滑入并放大高亮」：`enter` 用 `slideUp`，`active.scale` 设 `1.2`；想让旧行「淡出缩小」：`exit` 用 `fade`，`inactive.scale` 设 `0.9`。

### 4.4 逐字配色 `karaoke`

```json
"karaoke": { "fill": "#34C7F4", "rest": "#666666" }
```
`fill` = 已唱到的字，`rest` = 未唱到的字。需配合 `enter.type = "karaoke"`。

---

## 五、完整示例

`示例主题/` 目录里放了可直接导入的文件：

- `重做-极简黑白.json`（播放器）：白色大封面 + 顶部歌名 + 波浪进度盘 + 底部一字排开控制键。
- `重做-霓虹赛博.json`（播放器）：暗底青色、方形圆角封面、缩放入场。
- `歌词-霓虹逐字.json`（歌词）：逐字点亮 + 滑入高亮 + 背景辉光。
- `歌词-淡雅翻转.json`（歌词）：3D 翻转进入、淡出、无辉光。

把这些文件放进手表存储，用主题工坊导入即可看到效果，再照着改字段就是你的专属皮肤。

---

## 六、排错

| 现象 | 原因 / 解决 |
| --- | --- |
| 导入后列表看不到 | `kind` 拼写错误，或不是合法 JSON；version 必须是 `2` |
| 应用后没变化 | 确认点亮了主题卡片（显示「使用中」）；播放器/歌词是两类，分别应用 |
| 控件跑到圆屏外被裁 | `x/y` 收进 `0.15~0.85` 安全区 |
| 颜色不对 | 颜色必须是带 `#` 的十六进制字符串，如 `"#34C7F4"` |
| 想恢复原样 | 主题工坊 → 恢复默认主题 |
| 波浪盘不转 / 太大 | 调 `progress` 的 `waveSpeed`（周期）与 `frac`（直径比例） |

---

## 七、字段速查（复制改）

播放器最小可用皮肤：
```json
{"version":2,"kind":"player","name":"极简","bg":"#000000","widgets":[
  {"id":"cover","x":0.5,"y":0.32,"frac":0.5,"shape":"circle"},
  {"id":"title","x":0.5,"y":0.62,"fontSize":16,"bold":true},
  {"id":"progress","x":0.5,"y":0.5,"frac":0.9,"style":"ring","stroke":6},
  {"id":"play","x":0.5,"y":0.84,"w":60,"h":60,"bg":"#34C7F4"}
]}
```

歌词最小可用皮肤：
```json
{"version":2,"kind":"lyrics","name":"简洁","align":"center",
 "active":{"color":"#34C7F4","size":18,"bold":true,"scale":1.2},
 "inactive":{"color":"#888888","size":14,"opacity":0.5},
 "enter":{"type":"slideUp","dur":320},"exit":{"type":"fade","dur":220}}
```
