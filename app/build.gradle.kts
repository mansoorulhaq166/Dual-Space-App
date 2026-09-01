plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.dual.space"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.dual.space"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Dual Space Pro-style host Play services Google login. Set true to restore in-clone
        // microG (GmsProvisioner install/warm + LoginActivity). Assets stay in DualCore either way.
        buildConfigField("boolean", "USE_MICROG_GOOGLE_LOGIN", "false")
    }

    flavorDimensions += "edition"
    productFlavors {
        // Full (5 clones/package) vs Lite (dual-account, 2 clones/package).
        // Both ship arm64-v8a + armeabi-v7a; ABI splits were dropped (native libs are tiny vs microG assets).
        create("full") {
            dimension = "edition"
            isDefault = true
            buildConfigField("boolean", "IS_LITE", "false")
            buildConfigField("int", "MAX_CLONES_PER_PACKAGE", "5")
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "IS_LITE", "true")
            buildConfigField("int", "MAX_CLONES_PER_PACKAGE", "2")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // androidTest is minified separately when the app is minified; it does NOT inherit
            // proguardFiles. Without this, R8 fails on JDK-only types (javax.lang.model.*)
            // referenced by errorprone annotations pulled into the test classpath.
            testProguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-android-test.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Debug-signed so sideloaded release builds are installable out of the box. This app embeds
            // a hooking virtualization engine and isn't Play-distributable as-is; swap in a real release
            // keystore here if/when you distribute it.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            testProguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-android-test.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // APPLICATION_ID, IS_LITE, MAX_CLONES_PER_PACKAGE, USE_MICROG_GOOGLE_LOGIN
    }
}

// versionCode = base * 10 + editionCode (Full/Lite update independently on Play).
androidComponents {
    onVariants { variant ->
        val edition = variant.productFlavors.firstOrNull { it.first == "edition" }?.second
        val editionCode = if (edition == "lite") 1 else 0
        val base = android.defaultConfig.versionCode ?: 1
        val code = base * 10 + editionCode
        variant.outputs.forEach { output ->
            output.versionCode.set(code)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val localDualCore = rootProject.findProject(":DualCore")
    if (localDualCore != null) {
        // Local development against the engine repository stored outside this public repository.
        implementation(project(":DualCore"))
    } else {
        // CI/release builds resolve the binary from your private Maven/GitHub Packages registry.
        // The source code never enters this repository.
        val dualCoreGroup = providers.gradleProperty("dualCoreGroup")
            .orElse("com.dualspace.private")
            .get()
        val dualCoreArtifact = providers.gradleProperty("dualCoreArtifact")
            .orElse("dualcore-engine")
            .get()
        val dualCoreVersion = providers.gradleProperty("dualCoreVersion")
            .orElse("3.1.8-private.1")
            .get()
        implementation("$dualCoreGroup:$dualCoreArtifact:$dualCoreVersion")
    }
    implementation(files("libs/pine-core-0.3.0.aar"))
    implementation(libs.google.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.toml4j)
    implementation(libs.free.reflection)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lsposed.hiddenapibypass)
    implementation(libs.androidx.work.runtime)
    // Applies the bundled baseline profile (src/main/baseline-prof.txt) on install — incl. Android
    // 10 (API 29) via ProfileInstaller — to AOT-compile startup hot paths and cut cold-start time.
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}