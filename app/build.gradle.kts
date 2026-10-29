import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from environment variables (set by CI from repository secrets).
// Without them the release build falls back to the debug key so it still installs.
// `-Pdemo` builds app.familiar.demo with made-up sessions for screenshots, next to the real install.
val demo = project.hasProperty("demo")
// `-Pdrawn` with it: every demo book gets a drawn cover, for store screenshots without publishers' art.
val drawn = project.hasProperty("drawn")
// `-Pdemolang=en|ru` with it: the language the demo is written in, whatever the phone speaks.
val demoLang = (project.findProperty("demolang") as String?).orEmpty()

val releaseKeystore = System.getenv("SIGNING_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "app.familiar"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.familiar"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        buildConfigField("boolean", "DEMO", demo.toString())
        buildConfigField("boolean", "DRAWN_COVERS", drawn.toString())
        buildConfigField("String", "DEMO_LANG", "\"$demoLang\"")
        if (demo) applicationIdSuffix = ".demo"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Keeps a debug install from ever replacing (or wiping) the real app on the same phone.
            if (!demo) applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    // Play otherwise installs only the device's languages from the AAB,
    // and the in-app switch to Russian would find no Russian strings.
    bundle {
        language {
            enableSplit = false
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        error += "MissingTranslation"
        // Dependency versions are pinned on purpose (AGP 8.13 + Kotlin 2.2 toolchain).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // Adaptive icons stay in mipmap-anydpi-v26: aapt does not resolve them from plain mipmap-anydpi.
        disable += "ObsoleteSdkInt"
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // QR visiting cards: drawing and reading them (from the camera or a picture), all on the phone.
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded) { isTransitive = false }
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// The art renderer (`-Prender`) draws every familiar to app/build/art for a look; it's not a test.
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("render")) exclude("**/RenderArt*")
    systemProperty("render.out", layout.buildDirectory.dir("art").get().asFile.absolutePath)
    systemProperty("render.res", file("src/main/res").absolutePath)
    systemProperty("render.docs", rootProject.file("docs").absolutePath)
    systemProperty("render.lang", (project.findProperty("lang") ?: "en").toString())
}
