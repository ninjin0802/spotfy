import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ninjin.spotifyweb"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ninjin.spotifyweb"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.11"
    }
    buildFeatures { buildConfig = true }
    // 署名鍵の情報はリポジトリの外（~/.config/spotfy/keystore.properties）に置く
    val keystoreProps = Properties().apply {
        val f = file(System.getenv("SPOTFY_KEYSTORE_PROPERTIES") ?: "${System.getProperty("user.home")}/.config/spotfy/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
}
