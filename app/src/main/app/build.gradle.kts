plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.linuxvm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.linuxvm"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    lint { abortOnError = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
