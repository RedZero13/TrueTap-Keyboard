import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional: put GIPHY_API_KEY=... in local.properties (or the environment) to bake in a key.
// Users can also paste a key in the app's settings.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val giphyKey: String = localProps.getProperty("GIPHY_API_KEY") ?: System.getenv("GIPHY_API_KEY") ?: ""

android {
    namespace = "com.truetap.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.truetap.keyboard"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIPHY_API_KEY", "\"$giphyKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK installs straight away; use your own key for the Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.all { it.workingDir = projectDir }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
