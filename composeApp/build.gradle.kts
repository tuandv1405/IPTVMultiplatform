import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.ksp) // For Room
    alias(libs.plugins.room) // For Room
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Required when using NativeSQLiteDriver
            linkerOpts.add("-lsqlite3")
            
            // Firebase framework linker options for iOS
//            linkerOpts.addAll(listOf("-framework", "FirebaseCore"))
//            linkerOpts.addAll(listOf("-framework", "FirebaseFirestore"))
//            linkerOpts.addAll(listOf("-framework", "FirebaseAuth"))
//            linkerOpts.addAll(listOf("-framework", "FirebaseAnalytics"))
//            linkerOpts.addAll(listOf("-framework", "FirebaseCrashlytics"))
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

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

            // Add Media3 dependencies for Android
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.ui)
            implementation(libs.media3.session)
            implementation(libs.media3.common)

            // Add Firebase dependencies for Android
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.crashlytics)
            // Add Firebase App Check dependencies
            implementation("com.google.firebase:firebase-appcheck-playintegrity")
            implementation("com.google.firebase:firebase-appcheck-debug")
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
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.common.compose)

            // Add common Ktor dependencies
            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.ktor.logging)
            implementation(libs.ktor.client.encoding)

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
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)

            implementation(libs.coil.compose)
            implementation(libs.coil.network)
            implementation(libs.haze.blur)

            implementation(libs.firebase.common)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.firestore)

            implementation(libs.ktor.serialization.kotlinx.xml)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        iosArm64Main.dependencies {
            implementation(libs.ktor.ios)
        }

        iosSimulatorArm64Main.dependencies {
            implementation(libs.ktor.ios)
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
    compileSdkVersion(libs.versions.android.compileSdk.get().toInt())

    defaultConfig {
        applicationId = "tss.t.tsiptv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 25401
        versionName = "ts.iptv.v$versionCode"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)

    // Core library desugaring for Firebase compatibility
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.0")

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
