# TENGmusic R8 混淆规则
# 应用本身（com.tengwear.tengmusic）保持混淆以达成「混淆」目标；
# 以下仅保留运行所必需、或反射/清单注册的入口，避免被误删。

# 保留注解与 Kotlin 元数据（Compose / 序列化兼容）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation { *; }

# 四大组件入口：声明在 AndroidManifest 中，R8 通常自动保留，
# 这里显式保留以防全模式优化误伤
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Compose 运行时元数据 / 稳定性推断
-keep class androidx.compose.** { *; }
-keep class androidx.wear.compose.** { *; }

# Media3 / ExoPlayer：避免内部类被过度裁剪
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil 图片加载（无反射，保留以防资源裁剪警告）
-dontwarn coil.**

# 不警告常见无关依赖
-dontwarn org.conscrypt.**
-dontwarn com.google.common.**
