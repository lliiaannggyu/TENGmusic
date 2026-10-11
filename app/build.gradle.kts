plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.wearmusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wearmusic"
        minSdk = 25          // Android 7.1：Wear OS 手表与普通安卓手机均可安装
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}

dependencies {
    val composeUi = "1.6.7"
    val wearCompose = "1.3.1"
    val media3 = "1.3.1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")
    implementation("androidx.compose.material:material-icons-core:$composeUi")
    implementation("androidx.compose.material:material-icons-extended:$composeUi")
    implementation("androidx.wear.compose:compose-material:$wearCompose")
    implementation("androidx.wear.compose:compose-foundation:$wearCompose")
    // 手机端 UI（普通安卓设备）
    implementation("androidx.compose.material3:material3:1.2.1")
    // 专辑封面加载（Coil）
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
}
