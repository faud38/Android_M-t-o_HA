plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro de compilation GitHub (1, 2, 3…), utilisé par la mise à jour automatique
val numero = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// Dépôt GitHub (« compte/meteo-widget ») où l'application cherche ses mises à jour
val depot = System.getenv("GITHUB_REPOSITORY") ?: ""
val cle = System.getenv("KEYSTORE_FILE")

android {
    namespace = "fr.meteowidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.meteowidget"
        minSdk = 26
        targetSdk = 34
        versionCode = numero
        versionName = "1.$numero"
        buildConfigField("String", "DEPOT", "\"$depot\"")
    }

    signingConfigs {
        create("release") {
            if (cle != null) {
                storeFile = file(cle)
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "meteo"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (cle != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.javascriptengine:javascriptengine:1.1.1")
}
