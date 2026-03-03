plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace   = "com.euhomy.fridge"
    compileSdk  = 34

    defaultConfig {
        applicationId = "com.euhomy.fridge"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    // ── Build flavors ──────────────────────────────────────────────────────
    flavorDimensions += "variant"
    productFlavors {
        create("production") {
            dimension    = "variant"
            // No changes — clean first-launch setup
        }
        create("dev") {
            dimension    = "variant"
            // Settings screen always accessible, extra dev tools
            applicationIdSuffix = ".dev"
        }
        create("private") {
            dimension    = "variant"
            // Owner-only build: credentials baked in, connects directly.
            // ⚠️  Never push src/private/ to any public repository.
            applicationIdSuffix = ".private"
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
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.coroutines.android)
}
