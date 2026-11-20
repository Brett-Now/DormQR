plugins {
    id("com.android.application")
}

android {
    namespace = "app.dormqr.qr"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.dormqr.qr"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 本项目零第三方依赖：网络用 HttpURLConnection，JSON 用框架自带 org.json，
    // 二维码编码器为内置的 io.nayuki.qrcodegen（MIT）。
}


