import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.ksp) // For Room
    alias(libs.plugins.room) // For Room
}

// Using version catalog for dependencies

// Exclude Android-specific dependencies from iOS targets
//configurations {
//    all {
//        if (name.contains("ios", ignoreCase = true)) {
//            exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
//        }
//    }
//}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Required when using NativeSQLiteDriver
            linkerOpts.add("-lsqlite3")
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.navigation.compose)

            // Add Android-specific Ktor dependencies with Cronet and OkHttp
            implementation(libs.ktor.android)
            implementation(libs.ktor.okhttp)

            // Add Room dependencies

            // Add SharedPreferences dependencies
            implementation(libs.androidx.preference)

            // Room is now used for database access

            // Add Koin for Android and Compose
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Add common Ktor dependencies
            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.ktor.logging)

            // Add Kotlin Serialization
            implementation(libs.kotlinx.serialization.json)

            // Room for multiplatform database access
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // Add Multiplatform Settings dependencies
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Add Kotlinx DateTime for multiplatform date/time operations
            implementation(libs.kotlinx.datetime)

            // Add Koin for dependency injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose.jb)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // Add iOS-specific dependencies to each iOS target
        iosX64Main.dependencies {
            implementation(libs.ktor.ios)
            // Room for iOS
        }

        iosArm64Main.dependencies {
            implementation(libs.ktor.ios)
            // Room for iOS
        }

        iosSimulatorArm64Main.dependencies {
            implementation(libs.ktor.ios)
            // Room for iOS
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // Add Desktop-specific Ktor dependencies
            implementation(libs.ktor.cio)

            implementation("uk.co.caprica:vlcj:5.0.0-M4")
            implementation("uk.co.caprica:vlcj-natives:5.0.0-M4")

        }
    }
}

android {
    namespace = "tss.t.tsiptv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tss.t.tsiptv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)

    // Room is used for all platforms
}

compose.desktop {
    application {
        mainClass = "tss.t.tsiptv.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tss.t.tsiptv"
            packageVersion = "1.0.0"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
