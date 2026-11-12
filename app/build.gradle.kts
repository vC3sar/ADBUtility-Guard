import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adbutility.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adbutility.guard"
        minSdk = 29
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            val envPassword = System.getenv("KEYSTORE_PASSWORD")
            val envAlias = System.getenv("KEY_ALIAS")
            val envKeyPassword = System.getenv("KEY_PASSWORD")
            val keystoreFile = rootProject.file(
                System.getenv("KEYSTORE_FILE") ?: "release.keystore"
            )

            if (keystorePropsFile.exists()) {
                val props = Properties()
                keystorePropsFile.inputStream().use { props.load(it) }
                val propStoreFile = props.getProperty("storeFile")?.trim().orEmpty()
                val resolvedStore = if (propStoreFile.isNotEmpty()) {
                    rootProject.file(propStoreFile)
                } else {
                    keystoreFile
                }
                storeFile = resolvedStore
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else if (keystoreFile.exists() && !envPassword.isNullOrBlank() && !envAlias.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = envPassword
                keyAlias = envAlias
                keyPassword = envKeyPassword ?: envPassword
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ""
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
}
