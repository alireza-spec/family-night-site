plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.alirezaspec.familynight"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.alirezaspec.familynight"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Release signing is wired by CI: the workflow decodes the
            // KEYSTORE_BASE64 secret into app/keystore.p12 and passes the
            // passwords as environment variables (KEYSTORE_PASSWORD,
            // KEY_ALIAS, KEY_PASSWORD). Without a keystore the config stays
            // empty and assembleRelease fails loudly instead of silently
            // producing an unsigned APK.
            val keystoreFile = file("keystore.p12")
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
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            // Debug builds install alongside the release app.
            applicationIdSuffix = ".debug"
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
    implementation("androidx.activity:activity-ktx:1.9.2")
    // Media3 (ExoPlayer) 1.3.1: MKV container + H.265/x265 + MP4 playback.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
}
