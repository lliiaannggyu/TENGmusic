plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tengwear.tengmusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tengwear.tengmusic"
        minSdk = 25          // Android 7.1：Wear OS 手表与普通安卓手机均可安装
        targetSdk = 36
        versionCode = 20
        versionName = "1.20"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val props = rootProject.file("local.properties").let { f ->
                if (f.exists()) {
                    f.readLines()
                        .map { it.split("=", limit = 2) }
                        .filter { it.size == 2 }
                        .associate { it[0].trim() to it[1].trim() }
                } else emptyMap()
            }
            storeFile = rootProject.file(props["RELEASE_STORE_FILE"] ?: "release-key.keystore")
            storePassword = props["RELEASE_STORE_PASSWORD"]
            keyAlias = props["RELEASE_KEY_ALIAS"]
            keyPassword = props["RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            // R8 压缩 + 混淆：缩减体积、移除无用代码并打乱类名/方法名
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            // 旋转表冠（rotary）输入 API
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

dependencies {
    // Compose BOM 统一管理 ui / foundation / runtime / material3 / material-icons 版本
    // 2026.06.01 → Compose 1.11.4 + material3 1.4.0（Material 3 Expressive）
    // （2026.09.00 的 Compose 1.12.1 要求 AGP 9.1 + compileSdk 37，AGP 8.13 不支持）
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // ---- Compose 核心（版本由 BOM 决定）----
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // ---- Wear OS 官方 Material 3 组件（手表端，纯手表应用）----
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")

    // 专辑封面加载
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 播放内核
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.wear.compose:compose-ui-tooling:1.6.2")
}
