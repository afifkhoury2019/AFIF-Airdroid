plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.afif.link"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.afif.link"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        resValue("string", "app_version_name", versionName)
    }

    signingConfigs {
        create("ci") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed only when CI supplies a keystore; otherwise a plain unsigned release.
            if (System.getenv("SIGNING_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
