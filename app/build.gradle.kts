import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String? = localProperties.getProperty(name)

fun releaseConfig(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: localProperty(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.jussicodes.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jussicodes.music"
        minSdk = 26
        targetSdk = 34
        // canary 线独立发版：版本号必须高于公开仓库已发布的正式版（2.5.4 / 54），
        // 否则 App 内显示的版本号看起来比正式版还旧（Issue #3 报告者就是拿它对比的）。
        versionCode = 56
        versionName = "2.5.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseConfig("RELEASE_STORE_FILE")?.let { releaseStoreFile ->
            register("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseConfig("RELEASE_STORE_PASSWORD")
                keyAlias = releaseConfig("RELEASE_KEY_ALIAS")
                keyPassword = releaseConfig("RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        register("canary") {
            applicationIdSuffix = ".canary"
            // CI 通过 -PCANARY_VERSION_SUFFIX=-canary.<UTC 月日-时分秒> 注入唯一后缀（见 build.yml），默认 -canary
            versionNameSuffix = (findProperty("CANARY_VERSION_SUFFIX") as? String) ?: "-canary"
            // canary 与正式版同样开启 R8 和资源压缩，避免未优化包自身造成掉帧或启动差异。
            // 需要快速迭代时显式传 -PcanaryMinify=false 关闭；isShrinkResources 必须同步，
            // AGP 8.5 下单独开启资源压缩会直接报错。
            val canaryMinify = (findProperty("canaryMinify") as? String)?.toBoolean() ?: true
            isMinifyEnabled = canaryMinify
            isShrinkResources = canaryMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // canary 专用测试签名：keystore 由 CI(keytool) 构建时生成，不依赖 secrets，指纹每次不同可接受
            val canaryStore = rootProject.file("canary-keystore.jks")
            if (canaryStore.exists()) {
                signingConfigs.create("canary") {
                    storeFile = canaryStore
                    storePassword = "jussichords-canary"
                    keyAlias = "jussichords-canary"
                    keyPassword = "jussichords-canary"
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                }
                signingConfig = signingConfigs.getByName("canary")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/*.version", // https://stackoverflow.com/a/58956288
                "META-INF/**/LICENSE.txt",
                "DebugProbesKt.bin", // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
                "kotlin-tooling-metadata.json"
            )
            pickFirsts += "META-INF/androidx.compose.ui_ui.version" // For Layout Inspector
        }
    }

    // https://stackoverflow.com/a/77745844
    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
                register("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    // 应用 baseline-prof.txt：安装时把启动关键类预编译成 AOT，缩短更新后首启的解释执行窗口
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    // 官方服务器样式图标 Icons.Outlined.Dns（core 里没有 Dns/Lan/Storage，只有 core 的 ~60 个）
    implementation(libs.androidx.material.icons.extended)
    // 扫码登录：本地生成网易云 qrcode 登录码（纯 java，无 Android 依赖）
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.datastore)
    implementation(libs.datastore.preferences)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.media3)
    implementation(libs.media3.database)
    implementation(libs.media3.datasource)
    implementation(libs.media3.session)
    // 桌面小组件：Glance（minSdk 26 可用；1.1.1 与 compileSdk 34 匹配）
    implementation(libs.androidx.glance.appwidget)
    // Glance 是 implementation 依赖，不暴露 WorkManager 和 EmojiCompat 的编译期 classpath，需显式声明。
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.reorderable)
    implementation(project(":ncmapi"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
