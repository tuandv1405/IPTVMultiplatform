import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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
            // #KODIPROP mimetype / manifest_type=ism; without it Media3 cannot build the source.
            implementation(libs.media3.exoplayer.smoothstreaming)
            implementation(libs.media3.ui)
            implementation(libs.media3.session)
            implementation(libs.media3.common)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.okhttp.dnsoverhttps)

            // Add Firebase dependencies for Android
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.crashlytics)
            // Push notifications (docs/prd-push-notifications.md)
            implementation(libs.firebase.messaging)
            // Add Firebase App Check dependencies
            implementation("com.google.firebase:firebase-appcheck-playintegrity")
            implementation("com.google.firebase:firebase-appcheck-debug")

            // Play In-App Review for the rating prompt (docs/prd-rate-app.md)
            implementation(libs.play.review.ktx)

            // AdMob + Google UMP consent (docs/prd-admob.md)
            implementation(libs.play.services.ads)
            implementation(libs.user.messaging.platform)

            // Google Play Billing: subscriptions (docs/prd-subscriptions.md)
            implementation(libs.play.billing)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(libs.compose.ui.backhandler)
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

        // Room migration tests run on the JVM against the exported schemas/.
        val desktopTest by getting {
            dependencies {
                implementation(libs.room.testing)
            }
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

// The app's version, in one place. Everything else is derived, so the number
// Play orders releases by and the name a person reads can never disagree.
//
// versionPatch has four digits, giving 10,000 builds inside a minor version;
// the encoding leaves room for major versions up to 2100 before Play's
// versionCode ceiling of 2,100,000,000 is a concern.
val versionMajor = 1
val versionMinor = 1
val versionPatch = 1

// 1.0.1 -> 1_000_001. Strictly increasing as long as the three parts only go up,
// and already above the 26301 this replaced.
val appVersionCode = versionMajor * 1_000_000 + versionMinor * 10_000 + versionPatch

// e.g. tsptv.1.0.0001
val appVersionName = "tsptv.$versionMajor.$versionMinor.${versionPatch.toString().padStart(4, '0')}"

// Exposes the version name to common code (the Stremio addon client's User-Agent,
// "TSIPTV/{version} (Stremio-addon-client)"), so it cannot drift from the build.
val generateAppBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/appbuildinfo/commonMain/kotlin")
    val versionName = appVersionName
    inputs.property("versionName", versionName)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("tss/t/tsiptv/AppBuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package tss.t.tsiptv\n\n" +
                "/** Generated by `generateAppBuildInfo` in composeApp/build.gradle.kts. Do not edit. */\n" +
                "object AppBuildInfo {\n" +
                "    const val VERSION_NAME: String = \"$versionName\"\n" +
                "}\n"
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateAppBuildInfo) }

// Upload-key credentials for the Play Console release build. Resolved from
// composeApp/keystore.properties first, then from the environment so CI can
// supply them without a file on disk. Both are untracked; see
// play-store/RELEASE-CHECKLIST.md for how to create the key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("composeApp/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun keystoreSetting(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = keystoreSetting("storeFile", "TSIPTV_STORE_FILE")
val releaseStorePassword = keystoreSetting("storePassword", "TSIPTV_STORE_PASSWORD")
val releaseKeyAlias = keystoreSetting("keyAlias", "TSIPTV_KEY_ALIAS")
val releaseKeyPassword = keystoreSetting("keyPassword", "TSIPTV_KEY_PASSWORD")

// AdMob (docs/prd-admob.md §6). Debug always uses Google's public test IDs. Release reads the real
// IDs from a Gradle property, composeApp/local.properties / local.properties, or the environment,
// in that order, and falls back to the test IDs with a warning. Never commit real IDs.
object AdMobTestIds {
    const val APP = "ca-app-pub-3940256099942544~3347511713"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}

val adMobLocalProperties = Properties().apply {
    listOf(rootProject.file("local.properties"), rootProject.file("composeApp/local.properties"))
        .filter { it.exists() }
        .forEach { file -> file.inputStream().use { load(it) } }
}

fun adMobSetting(name: String, testId: String): String {
    val value = (project.findProperty(name) as String?)
        ?: adMobLocalProperties.getProperty(name)
        ?: System.getenv(name)
    if (value.isNullOrBlank()) {
        if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
            logger.warn("composeApp: $name is not set - the release build uses the AdMob TEST id. See docs/handoff-admob.md.")
        }
        return testId
    }
    return value.trim()
}

val releaseAdMobAppId = adMobSetting("TSIPTV_ADMOB_APP_ID", AdMobTestIds.APP)
val releaseAdMobAppOpenUnit = adMobSetting("TSIPTV_ADMOB_APP_OPEN_UNIT", AdMobTestIds.APP_OPEN)
val releaseAdMobBannerUnit = adMobSetting("TSIPTV_ADMOB_BANNER_UNIT", AdMobTestIds.BANNER)
val releaseAdMobNativeUnit = adMobSetting("TSIPTV_ADMOB_NATIVE_UNIT", AdMobTestIds.NATIVE)
// Extra send / sync tasks (docs/prd-tv-cast-and-sync.md §3.3).
val releaseAdMobRewardedUnit = adMobSetting("TSIPTV_ADMOB_REWARDED_UNIT", AdMobTestIds.REWARDED)

// Subscriptions (docs/prd-subscriptions.md §5). Product ids default to the PRD's; the billing server
// URL is empty until it is deployed (then the app trusts only local Play state, PRD §5.3).
// Same lookup order as AdMob: Gradle property, local.properties, environment.
fun billingSetting(name: String, default: String): String =
    ((project.findProperty(name) as String?) ?: adMobLocalProperties.getProperty(name) ?: System.getenv(name))
        ?.trim()?.takeIf { it.isNotEmpty() } ?: default

val billingNoAdsId = billingSetting("TSIPTV_BILLING_NOADS_ID", "tsiptv_noads")
val billingUnlimitedId = billingSetting("TSIPTV_BILLING_UNLIMITED_ID", "tsiptv_unlimited")
val billingVerifyUrl = billingSetting("TSIPTV_BILLING_VERIFY_URL", "")

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "tss.t.tsiptv"
    compileSdkVersion(libs.versions.android.compileSdk.get().toInt())

    defaultConfig {
        applicationId = "tss.t.tsiptv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        resValue("string", "billing_product_noads", billingNoAdsId)
        resValue("string", "billing_product_unlimited", billingUnlimitedId)
        resValue("string", "billing_verify_url", billingVerifyUrl)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        // F2 QC (AC-S22): a DEBUG-ONLY override of the addon blocklist URL, e.g.
        //   gradlew :composeApp:assembleDebug -Ptsiptv.debugAddonBlocklistUrl=http://10.0.2.2:7000/policy/addon-blocklist.json
        // Release builds never define this resource, and the app reads it only when debuggable.
        getByName("debug") {
            resValue(
                "string",
                "debug_addon_blocklist_url",
                (project.findProperty("tsiptv.debugAddonBlocklistUrl") as String?).orEmpty(),
            )
            // AdMob: Google test IDs only.
            manifestPlaceholders["admobAppId"] = AdMobTestIds.APP
            resValue("string", "admob_app_open_unit", AdMobTestIds.APP_OPEN)
            resValue("string", "admob_banner_unit", AdMobTestIds.BANNER)
            resValue("string", "admob_native_unit", AdMobTestIds.NATIVE)
            resValue("string", "admob_rewarded_unit", AdMobTestIds.REWARDED)
            // QA: `-Ptsiptv.debugAdsNoFirstDay=true` skips the 24 h ad-free period (debug only).
            resValue(
                "bool",
                "debug_ads_skip_first_day",
                ((project.findProperty("tsiptv.debugAdsNoFirstDay") as String?) == "true").toString(),
            )
            // QA: `-Ptsiptv.debugDemoBilling=true` shows sample plan prices, nothing purchasable (debug only).
            resValue(
                "bool",
                "debug_demo_billing",
                ((project.findProperty("tsiptv.debugDemoBilling") as String?) == "true").toString(),
            )
            // QA: `-Ptsiptv.debugAppOpenTimeoutMs=8000` lets slow emulators show the app open ad (debug only).
            resValue(
                "integer",
                "debug_app_open_timeout_ms",
                ((project.findProperty("tsiptv.debugAppOpenTimeoutMs") as String?)?.toLongOrNull() ?: 0L).toString(),
            )
            // QA: `-Ptsiptv.debugSkipLogin=true` opens Home signed out on a phone (debug only).
            resValue(
                "bool",
                "debug_skip_login",
                ((project.findProperty("tsiptv.debugSkipLogin") as String?) == "true").toString(),
            )
            // QA: UMP consent as if in a region (debug only), e.g.
            //   -Ptsiptv.debugUmpGeography=EEA -Ptsiptv.debugUmpTestDevice=<hash from logcat> -Ptsiptv.debugUmpReset=true
            // Values: EEA, REGULATED_US_STATE, OTHER (empty = real location). Emulators are test devices.
            resValue(
                "string",
                "debug_ump_geography",
                (project.findProperty("tsiptv.debugUmpGeography") as String?).orEmpty(),
            )
            resValue(
                "string",
                "debug_ump_test_device",
                (project.findProperty("tsiptv.debugUmpTestDevice") as String?).orEmpty(),
            )
            resValue(
                "bool",
                "debug_ump_reset",
                ((project.findProperty("tsiptv.debugUmpReset") as String?) == "true").toString(),
            )
        }
        getByName("release") {
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId
            resValue("string", "admob_app_open_unit", releaseAdMobAppOpenUnit)
            resValue("string", "admob_banner_unit", releaseAdMobBannerUnit)
            resValue("string", "admob_native_unit", releaseAdMobNativeUnit)
            resValue("string", "admob_rewarded_unit", releaseAdMobRewardedUnit)
            resValue("bool", "debug_ads_skip_first_day", "false")
            resValue("bool", "debug_demo_billing", "false")
            resValue("bool", "debug_skip_login", "false")
            resValue("integer", "debug_app_open_timeout_ms", "0")
            resValue("string", "debug_ump_geography", "")
            resValue("string", "debug_ump_test_device", "")
            resValue("bool", "debug_ump_reset", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Leave the build unsigned rather than silently falling back to the
                // debug key, which the Play Console rejects.
                logger.warn(
                    "composeApp: no upload key configured - the release build will be unsigned. " +
                        "See play-store/RELEASE-CHECKLIST.md."
                )
            }
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

        buildTypes.release.proguard {
            // Android libraries leak onto the desktop runtime classpath, so
            // ProGuard sees ~11,700 unresolvable android.* references and refuses
            // to run. Shrinking is off until that dependency leak is fixed; the
            // installer is larger than it needs to be but correct.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // What a person sees in the installer, the Start Menu and Add/Remove
            // Programs. The old value was the Java package name.
            packageName = "TS IPTV"
            description = "Play your own M3U, XSPF and JSON IPTV playlists, with EPG."
            vendor = "TSS"
            copyright = "© 2026 TSS"

            // MSI accepts MAJOR.MINOR.BUILD only, so the four-digit patch of
            // appVersionName cannot be used verbatim here.
            packageVersion = "$versionMajor.$versionMinor.$versionPatch"

            // jlink strips the bundled runtime to what it can prove is used, and
            // it cannot see through reflection. Without these the packaged app
            // dies on startup with a bare "Failed to launch JVM" dialog.
            // Regenerate with: ./gradlew :composeApp:suggestRuntimeModules
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
            )

            windows {
                iconFile.set(rootProject.file("brand/app-icon.ico"))
                menu = true
                shortcut = true
                // A stable upgrade code lets a new MSI replace an installed copy
                // instead of sitting beside it. Never change this for this app.
                upgradeUuid = "8f3c1d6e-2b4a-4d5f-9a71-6c0e5b2d84f7"
            }

            linux {
                iconFile.set(rootProject.file("brand/app-icon-512.png"))
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
