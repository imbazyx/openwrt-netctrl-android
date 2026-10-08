import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.netctrl.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.netctrl.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Кystore берём из keystore.properties или из KEYSTORE_PASS.
        // Если его нет — релиз просто собирается без подписи, а не падает.
        create("release") {
            val props = Properties()
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { props.load(it) }
            val pass = props.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASS")
            val store = props.getProperty("storeFile")
            if (pass != null && store != null && rootProject.file(store).exists()) {
                storeFile = rootProject.file(store)
                storePassword = pass
                keyAlias = props.getProperty("keyAlias") ?: "netctrl"
                keyPassword = props.getProperty("keyPassword") ?: pass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // подписываем только если keystore реально найден
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}