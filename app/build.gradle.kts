import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ottojimb.locker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ottojimb.locker"
        minSdk = 34
        targetSdk = 34
        // CI passes these from the git tag; local builds use the defaults.
        versionCode = System.getenv("LOCKER_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("LOCKER_VERSION_NAME") ?: "0.1.0"
    }

    val keystorePath = System.getenv("LOCKER_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("LOCKER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LOCKER_KEY_ALIAS")
                keyPassword = System.getenv("LOCKER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
