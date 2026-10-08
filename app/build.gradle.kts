plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.fbarcalar.focustag"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.fbarcalar.focustag"
        minSdk {
            version = release(33)
        }
        targetSdk {
            version = release(37)
        }
        // CI release builds pass these (see .github/workflows/release.yml); local builds keep the defaults.
        versionCode = providers.gradleProperty("focustag.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("focustag.versionName").orNull ?: "0.1.0"
    }

    // A fixed key lets each published APK install over the previous one. It comes from the
    // environment so it never lives in the repo; without it Android's per-machine debug key is used.
    val stableKeystore = providers.environmentVariable("FOCUSTAG_KEYSTORE_FILE").orNull
    signingConfigs {
        if (stableKeystore != null) {
            create("stable") {
                storeFile = file(stableKeystore)
                storePassword = providers.environmentVariable("FOCUSTAG_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("FOCUSTAG_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("FOCUSTAG_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            if (stableKeystore != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "1g"
                // Robolectric's SDK 37 FileDescriptor interceptor needs these on JDK 21.
                test.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.espresso.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}

// Lint reads generated unit-test sources; without this ordering it can race the KSP/Hilt
// generators when `lint` and `test` run in one invocation and fail on a missing file.
tasks.matching { it.name == "lintAnalyzeDebugUnitTest" }.configureEach {
    mustRunAfter("kspDebugUnitTestKotlin", "hiltJavaCompileDebugUnitTest")
}
