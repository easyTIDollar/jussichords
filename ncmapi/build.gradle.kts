plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rcmiku.ncmapi"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 与 :app 的 canary buildType 对齐，否则 app 的 canary 变体无法匹配 :ncmapi 的 variant
    buildTypes {
        register("canary") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    // zstd-jni 必须用 aar 变体：plain jar 把 .so 当资源塞进 jar，AGP 不提取进 APK
    //（播放首歌时 NCBL 编码调 Zstd 触发 UnsatisfiedLinkError 崩进程）。
    // 1.5.6-6 的 aar 无 aar-metadata（minCompileSdk<=34），1.5.7-x 声明 minCompileSdk=37 会挂 checkXxxAarMetadata。
    implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}@aar")
    testImplementation(libs.junit)
}
