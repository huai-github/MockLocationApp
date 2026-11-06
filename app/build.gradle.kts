plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mocklocation"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mocklocation"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }

    // 让产出的 APK 文件名带上版本号，例如 app-debug-v1.2.apk
    applicationVariants.all {
        outputs.all {
            val vName = versionName ?: "1.0"
            outputFileName = "app-debug-v$vName.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // OpenStreetMap 地图（开源、无需任何 API Key）
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
