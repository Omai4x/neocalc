import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing credentials live outside the build script (and outside version
// control) so the key and its passwords are never committed. The build still
// works without the file — it just produces an unsigned release.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/**
 * True unless an app bundle is being built, or splitting was explicitly turned
 * off. Read at configuration time, which is what the splits block needs.
 */
val buildingSplitApks: Boolean = run {
    val requested = gradle.startParameter.taskNames
    val bundleRequested = requested.any { it.contains("bundle", ignoreCase = true) }
    val override = (project.findProperty("abiSplits") as String?)?.toBooleanStrictOrNull()
    override ?: !bundleRequested
}

android {
    namespace = "com.omai.neocalc"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.omai.neocalc"
        minSdk = 23
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // v3 carries the signing lineage, which is what makes it possible to
            // rotate this key later without the app becoming a different app.
            // v1 stays on for API 23 devices, which predate v2 entirely.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    /**
     * Per-ABI APKs, for sideloading only.
     *
     * ML Kit's text recognition ships native libraries for four architectures
     * and they are 39 of the universal APK's 43 MB. A device only ever needs
     * one of them, so splitting cuts a sideloaded install to about a third.
     * The universal APK is still produced for when the target is unknown.
     *
     * An app bundle already splits by ABI on the server side, and AGP refuses
     * to build one while per-ABI APKs are configured. So the split is switched
     * off automatically whenever a bundle task is what was asked for, which
     * means `assembleRelease` and `bundleRelease` both just work with no flag
     * to remember. `-PabiSplits=false` forces it off for anything else.
     */
    splits {
        abi {
            isEnable = buildingSplitApks
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // The About screen shows the version, which has to come from somewhere
        // that cannot drift out of step with the manifest.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Vector icons in place of emoji everywhere outside the arcade. R8 keeps
    // only the ones actually referenced, so the release APK carries a handful.
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Rate alerts need a scheduler that survives reboots and Doze; nothing in
    // the platform does that without a lot of AlarmManager plumbing.
    implementation(libs.androidx.work.runtime)
    // On-device text recognition for scanning a price out of a photo. Bundled
    // rather than downloaded so the feature works the first time it is used.
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    // android.jar's org.json is a stub that throws "not mocked"; this puts a
    // real implementation on the *test* classpath only, so the JSON parsing
    // tests actually execute. Nothing extra ships in the APK.
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}