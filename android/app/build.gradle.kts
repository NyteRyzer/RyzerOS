plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version comes from the shared Python package so both editions stay in step.
val ryzerVersion: String = rootDir.parentFile.resolve("ryzer/__init__.py").readLines()
    .first { it.startsWith("VERSION") }.substringAfter("\"").substringBefore("\"")

android {
    namespace = "com.ryzer.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ryzer.os"
        minSdk = 26          // Android 8: covers every Anbernic, AYN, Retroid and AYANEO Android handheld
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = ryzerVersion
    }

    signingConfigs {
        // A fixed key so every build installs as an update over the previous one.
        // Override with your own key through the RYZER_KEYSTORE* environment variables.
        create("ryzer") {
            storeFile = file(System.getenv("RYZER_KEYSTORE") ?: "ryzer.jks")
            storePassword = System.getenv("RYZER_KEYSTORE_PASSWORD") ?: "ryzeros"
            keyAlias = System.getenv("RYZER_KEY_ALIAS") ?: "ryzer"
            keyPassword = System.getenv("RYZER_KEY_PASSWORD") ?: "ryzeros"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ryzer")
        }
        debug {
            signingConfig = signingConfigs.getByName("ryzer")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/ryzerAssets"))
}

// The 20 themes are shared with the Linux edition: copy them in at build time.
val copyThemes = tasks.register<Copy>("copyRyzerThemes") {
    from(rootDir.parentFile.resolve("ryzer/themes"))
    into(layout.buildDirectory.dir("generated/ryzerAssets/themes"))
}
tasks.named("preBuild") { dependsOn(copyThemes) }
