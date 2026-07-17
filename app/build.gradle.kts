import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.nanchat.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nanchat.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"

    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    implementation(libs.foundation)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.socketio.client)
    implementation(libs.security.crypto)
    implementation(libs.jnano) {
        // Pulls in JOCL (optional GPU work-generation) whose bundled Windows DLL trips up
        // bundletool's packaging validation; we never use jnano's PoW generation on-device.
        exclude(group = "org.jocl", module = "jocl")
    }
    implementation(libs.blake2b)
    implementation(libs.eddsa)
    implementation(libs.lazysodium.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.material.icons.extended)
    implementation(libs.graphics.shapes)
    implementation(libs.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
