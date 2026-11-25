plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val isDevBuild = System.getenv("IS_DEV_BUILD")?.equals("true", ignoreCase = true) ?: false

android {
    namespace = "com.app.autoclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.app.autoclicker"
        if (isDevBuild) {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "QuickClick Dev")
        } else {
            resValue("string", "app_name", "QuickClick")
        }
        minSdk = 24
        targetSdk = 34
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION_NAME") ?: if (isDevBuild) "1.0.0-dev" else "1.0.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
        debug {
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
