plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.myspatial"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.example.myspatial"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += setOf(".bundle", ".glb", ".ktx", ".usdz")
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.2") {
        exclude(group = "androidx.compose.ui", module = "ui")
        exclude(group = "androidx.compose.ui", module = "ui-graphics")
        exclude(group = "androidx.compose.ui", module = "ui-text")
    }
    implementation(platform("com.pico.spatial:bom:0.13.3"))
    implementation("com.pico.spatial.core:core")
    implementation("com.pico.spatial.tracking:tracking")
    implementation("com.pico.spatial.ui:platform")
    implementation("com.pico.spatial.ui:foundation")
    implementation("com.pico.spatial.ui:design")
}
