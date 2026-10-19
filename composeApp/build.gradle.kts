import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
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
            // Player › Clip: recordings rewritten as MP4 (no re-encoding for live clips).
            implementation(libs.media3.transformer)
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
            // Dispatchers.setMain: Android host tests have no main looper.
            implementation(libs.kotlinx.coroutinesTest)
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

            // vlcj 4.x is the line for libVLC 3 (the stable VLC). vlcj 5 needs libVLC 4, which has no
            // release: with it the player fails to load ("libvlc_media_get_meta_extra" missing).
            // vlcj-natives (native discovery) comes with it.
            implementation("uk.co.caprica:vlcj:4.12.1")

        }
    }
}

// The app's version, in one place. Everything else is derived, so the number
// Play orders releases by and the name a person reads can never disagree.
//
// versionPatch is raised by CI after every merge to main (.github/workflows/version-bump.yml);
// do not change it in a pull request. It has four digits, giving 10,000 builds inside a minor version;
// the encoding leaves room for major versions up to 2100 before Play's
// versionCode ceiling of 2,100,000,000 is a concern.
val versionMajor = 1
val versionMinor = 1
val versionPatch = 4

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

// Desktop "support the developer" promos that replace ads there (docs/desktop-build.md). Same lookup
// order as above. All optional: a promo without a configured way to pay is not shown.
val desktopSupportSettings = linkedMapOf(
    "SUPPORT_URL" to billingSetting("TSIPTV_SUPPORT_URL", ""),
    "BANK_ID" to billingSetting("TSIPTV_SUPPORT_BANK_ID", ""),
    "ACCOUNT_NO" to billingSetting("TSIPTV_SUPPORT_ACCOUNT_NO", ""),
    "ACCOUNT_NAME" to billingSetting("TSIPTV_SUPPORT_ACCOUNT_NAME", ""),
)

val generateDesktopSupportConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/supportconfig/desktopMain/kotlin")
    val settings = desktopSupportSettings
    inputs.property("settings", settings)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("tss/t/tsiptv/support/DesktopSupportConfig.kt").asFile
        file.parentFile.mkdirs()
        val fields = settings.entries.joinToString("\n") { (name, value) ->
            val literal = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
            "    const val $name: String = \"$literal\""
        }
        file.writeText(
            "package tss.t.tsiptv.support\n\n" +
                "/** Generated by `generateDesktopSupportConfig` in composeApp/build.gradle.kts. Do not edit. */\n" +
                "object DesktopSupportConfig {\n$fields\n}\n"
        )
    }
}
kotlin.sourceSets.getByName("desktopMain") { kotlin.srcDir(generateDesktopSupportConfig) }

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
            // The R8 mapping goes to (production) Crashlytics only when asked for: a local
            // `assembleRelease` must not upload. Enable with -Ptsiptv.uploadMapping=true, or in CI
            // (environment CI=true). See play-store/RELEASE-CHECKLIST.md.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled =
                    (project.findProperty("tsiptv.uploadMapping") as String?) == "true" ||
                    System.getenv("CI") == "true"
            }
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

// Desktop playback needs libVLC (vlcj is only the Java binding). The Windows and macOS packages bundle
// the official VLC build so the app plays on a machine without VLC; Linux uses the system VLC.
// See docs/desktop-build.md. To update: change the version and the SHA-256s published next to the
// zip/dmg files on download.videolan.org.
val vlcVersion = "3.0.24"
val vlcWin64Sha256 = "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"
val vlcWin64Zip = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")
// jpackage builds for the architecture of the JDK running it, so the Mac build bundles that libVLC.
val vlcMacArch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "intel64"
val vlcMacSha256 = mapOf(
    "arm64" to "64a89d93cdd30b0e97131743e246373db82d6826ea882d265d46d74b136da2b7",
    "intel64" to "1ef6c903e2dc026d4e58ddf7b2a9ed0eb6f8caf80a9a9f46f96490340b962707",
).getValue(vlcMacArch)
val vlcMacDmg = layout.buildDirectory.file("vlc/vlc-$vlcVersion-$vlcMacArch.dmg")
// The Mac dmg has no licence file (the Windows zip has COPYING.txt): take it from the same VLC tag.
val vlcLicenseSha256 = "dc626520dcd53a22f727af3ee42c770e56c97a64fe3adb063799d8ab032fe551"

/** Downloads [url] to [destination] unless it is already there, and fails if the SHA-256 differs. */
abstract class DownloadVerifiedFile : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val file = destination.get().asFile
        fun digest(): String = MessageDigest.getInstance("SHA-256").let { md ->
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        if (file.exists() && digest() == sha256.get()) return
        file.parentFile.mkdirs()
        URI(url.get()).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        val actual = digest()
        if (actual != sha256.get()) {
            file.delete()
            throw GradleException("${url.get()}: SHA-256 $actual, expected ${sha256.get()}")
        }
    }
}

val downloadVlcWin64 by tasks.registering(DownloadVerifiedFile::class) {
    url.set("https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip")
    sha256.set(vlcWin64Sha256)
    destination.set(vlcWin64Zip)
}

// Only what an embedded player needs: the GUI, streaming output and visualisation plugins are left
// out (about 35 MB). COPYING.txt ships with it: libVLC is LGPL-2.1+.
val prepareDesktopVlc by tasks.registering(Sync::class) {
    val root = "vlc-$vlcVersion/"
    from(zipTree(downloadVlcWin64.flatMap { it.destination })) {
        include("${root}libvlc.dll", "${root}libvlccore.dll", "${root}COPYING.txt", "${root}plugins/**")
        exclude("${root}plugins/gui/**", "${root}plugins/access_output/**", "${root}plugins/stream_out/**")
        exclude("${root}plugins/visualization/**")
        // Blu-ray menus (BD-J) are Java: the packager put these jars on the app's classpath.
        exclude("${root}plugins/access/*.jar")
        eachFile { path = path.removePrefix(root) }
    }
    // Except the file output Player > Clip writes through (`#std{access=file}`, about 120 KB).
    from(zipTree(downloadVlcWin64.flatMap { it.destination })) {
        include(
            "${root}plugins/stream_out/libstream_out_standard_plugin.dll",
            "${root}plugins/access_output/libaccess_output_file_plugin.dll",
        )
        eachFile { path = path.removePrefix(root) }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("desktop-resources/windows/vlc"))
}

val downloadVlcMac by tasks.registering(DownloadVerifiedFile::class) {
    url.set("https://download.videolan.org/pub/videolan/vlc/$vlcVersion/macosx/vlc-$vlcVersion-$vlcMacArch.dmg")
    sha256.set(vlcMacSha256)
    destination.set(vlcMacDmg)
}

val downloadVlcLicense by tasks.registering(DownloadVerifiedFile::class) {
    url.set("https://code.videolan.org/videolan/vlc/-/raw/$vlcVersion/COPYING.LIB")
    sha256.set(vlcLicenseSha256)
    destination.set(layout.buildDirectory.file("vlc/COPYING-$vlcVersion.txt"))
}

/**
 * Copies libVLC out of VLC.app in the official dmg, in VLC.app's own layout (`lib/` next to `plugins/`):
 * vlcj finds `lib/libvlc.dylib` on `jna.library.path` and sets the plugin path to `lib/../plugins`
 * (DesktopVlc.kt). macOS hosts only: the dmg is mounted with hdiutil.
 */
abstract class ExtractMacVlc : DefaultTask() {
    @get:InputFile abstract val dmg: RegularFileProperty
    @get:InputFile abstract val license: RegularFileProperty
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @get:javax.inject.Inject abstract val execOperations: ExecOperations
    @get:javax.inject.Inject abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        val mount = File(temporaryDir, "mnt")
        mount.mkdirs()
        execOperations.exec {
            commandLine("hdiutil", "attach", "-quiet", "-nobrowse", "-readonly", "-noautoopen", "-noverify",
                "-mountpoint", mount.absolutePath, dmg.get().asFile.absolutePath)
        }
        try {
            fileSystemOperations.sync {
                // The unversioned names are symlinks: the copy follows them, and libvlc.dylib asks
                // for @rpath/libvlccore.dylib by exactly that name.
                from(File(mount, "VLC.app/Contents/MacOS")) {
                    include("lib/libvlc.dylib", "lib/libvlccore.dylib", "plugins/*.dylib")
                    // VLC's own GUI and Growl notifications link frameworks that are not copied.
                    exclude("plugins/libmacosx_plugin.dylib", "plugins/libosx_notifications_plugin.dylib")
                    // Same cuts as Windows: streaming output and visualisations.
                    exclude("plugins/libaccess_output_*", "plugins/libstream_out_*")
                    exclude("plugins/libgoom_plugin.dylib", "plugins/libvisual_plugin.dylib")
                }
                // Except the file output Player > Clip writes through, as on Windows.
                from(File(mount, "VLC.app/Contents/MacOS")) {
                    include("plugins/libstream_out_standard_plugin.dylib", "plugins/libaccess_output_file_plugin.dylib")
                }
                from(license) { rename { "COPYING.txt" } }
                into(destination)
            }
        } finally {
            execOperations.exec { commandLine("hdiutil", "detach", "-quiet", mount.absolutePath) }
        }
    }
}

val prepareDesktopVlcMac by tasks.registering(ExtractMacVlc::class) {
    dmg.set(downloadVlcMac.flatMap { it.destination })
    license.set(downloadVlcLicense.flatMap { it.destination })
    destination.set(layout.buildDirectory.dir("desktop-resources/macos/vlc"))
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

            // build/desktop-resources/windows/vlc reaches the app as
            // <compose.application.resources.dir>/vlc (DesktopVlc.kt).
            appResourcesRootDir.set(layout.buildDirectory.dir("desktop-resources"))

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
                // Without it the Start Menu folder is called "Unknown".
                menuGroup = "TS IPTV"
                shortcut = true
                // Installs for the current user, under %LOCALAPPDATA%: no administrator prompt, so
                // TS-IPTV-<version>-Setup.exe installs in one click (packageDesktopRelease below).
                perUserInstall = true
                // A stable upgrade code lets a new MSI replace an installed copy
                // instead of sitting beside it. Never change this for this app.
                upgradeUuid = "8f3c1d6e-2b4a-4d5f-9a71-6c0e5b2d84f7"
            }

            macOS {
                // brand/app-icon-1024.png at 80 % on a transparent canvas, the margin macOS icons have.
                iconFile.set(rootProject.file("brand/app-icon.icns"))
                bundleID = "tss.t.tsiptv.desktop"
                dockName = "TS IPTV"
                appCategory = "public.app-category.video"
                // Unsigned unless TSIPTV_MAC_SIGN_IDENTITY is set (docs/desktop-build.md, macOS).
                billingSetting("TSIPTV_MAC_SIGN_IDENTITY", "").takeIf { it.isNotEmpty() }?.let { identity ->
                    signing {
                        sign.set(true)
                        this.identity.set(identity)
                    }
                    // Apple ID, an app-specific password (appleid.apple.com) and the team ID.
                    billingSetting("TSIPTV_MAC_NOTARY_APPLE_ID", "").takeIf { it.isNotEmpty() }?.let { id ->
                        notarization {
                            appleID.set(id)
                            password.set(billingSetting("TSIPTV_MAC_NOTARY_PASSWORD", ""))
                            teamID.set(billingSetting("TSIPTV_MAC_NOTARY_TEAM_ID", ""))
                        }
                    }
                }
            }

            linux {
                iconFile.set(rootProject.file("brand/app-icon-512.png"))
            }
        }
    }
}

// An installer is built on its own OS, so each host bundles only its own libVLC (macOS: also for
// ./gradlew :composeApp:run, which reads the same app resources).
val hostOs: String = System.getProperty("os.name")
when {
    hostOs.startsWith("Windows") ->
        tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareDesktopVlc) }
    hostOs.startsWith("Mac") ->
        tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareDesktopVlcMac) }
}

// Windows release: ./gradlew :composeApp:packageDesktopRelease (docs/desktop-build.md §2) writes to
// build/release/desktop/ a one-click TS-IPTV-<v>-Setup.exe, the MSI, a portable zip, README.txt and
// SHA256SUMS.txt. Windows hosts only: the MSI needs WiX and the Setup.exe IExpress (part of Windows).
val desktopVersion = "$versionMajor.$versionMinor.$versionPatch"
val desktopReleaseDir = layout.buildDirectory.dir("release/desktop")
val desktopPackaging = layout.projectDirectory.dir("packaging/windows")

/** Wraps [msi] and [installScript] in a self-extracting [setupExe] that runs the script (Windows IExpress). */
abstract class OneClickSetupExe : DefaultTask() {
    @get:InputFile abstract val msi: RegularFileProperty
    @get:InputFile abstract val installScript: RegularFileProperty
    @get:OutputFile abstract val setupExe: RegularFileProperty
    @get:javax.inject.Inject abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        val stage = File(temporaryDir, "files").apply { deleteRecursively(); mkdirs() }
        // install.cmd refers to these names.
        msi.get().asFile.copyTo(File(stage, "TS-IPTV.msi"))
        installScript.get().asFile.copyTo(File(stage, "install.cmd"))
        val target = setupExe.get().asFile.apply { parentFile.mkdirs(); delete() }
        // ShowInstallProgramWindow=1 hides the console; msiexec /passive shows its own progress bar.
        // .\install.cmd, not install.cmd: with NoDefaultCurrentDirectoryInExePath set, cmd does not
        // look in the current folder.
        val sed = File(temporaryDir, "setup.sed")
        sed.writeText(
            listOf(
                "[Version]", "Class=IEXPRESS", "SEDVersion=3",
                "[Options]", "PackagePurpose=InstallApp", "ShowInstallProgramWindow=1",
                "HideExtractAnimation=0", "UseLongFileName=1", "InsideCompressed=0", "CAB_FixedSize=0",
                "CAB_ResvCodeSigning=0", "RebootMode=N", "InstallPrompt=", "DisplayLicense=",
                "FinishMessage=", "TargetName=${target.absolutePath}", "FriendlyName=TS IPTV Setup",
                "AppLaunched=cmd /c .\\install.cmd", "PostInstallCmd=<None>", "AdminQuietInstCmd=",
                "UserQuietInstCmd=", "SourceFiles=SourceFiles",
                "[Strings]", "FILE0=\"TS-IPTV.msi\"", "FILE1=\"install.cmd\"",
                "[SourceFiles]", "SourceFiles0=${stage.absolutePath}\\",
                "[SourceFiles0]", "%FILE0%=", "%FILE1%=",
            ).joinToString("\r\n", postfix = "\r\n")
        )
        execOperations.exec { commandLine("iexpress.exe", "/N", "/Q", sed.absolutePath) }
        if (!target.isFile) throw GradleException("IExpress did not write $target")
    }
}

/** Writes [sums] in sha256sum format for [files]. */
abstract class Sha256Sums : DefaultTask() {
    @get:InputFiles abstract val files: ConfigurableFileCollection
    @get:OutputFile abstract val sums: RegularFileProperty

    @TaskAction
    fun write() {
        val lines = files.files.filter { it.isFile }.sortedBy { it.name }.map { file ->
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) } + " *" + file.name
        }
        // LF: `sha256sum -c` reads CR as part of the file name; Notepad shows LF fine.
        sums.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

// Each download is built in its own folder under release/stage, then packageDesktopRelease puts
// them together in release/desktop.
val desktopReleaseStage = layout.buildDirectory.dir("release/stage")

val desktopReleaseReadme by tasks.registering(Copy::class) {
    val version = desktopVersion
    from(desktopPackaging.file("README.txt"))
    filter { line: String -> line.replace("{VERSION}", version) }
    // Notepad users: CRLF whatever git checked out.
    filter(
        mapOf("eol" to org.apache.tools.ant.filters.FixCrLfFilter.CrLf.newInstance("crlf")),
        org.apache.tools.ant.filters.FixCrLfFilter::class.java,
    )
    into(desktopReleaseStage.map { it.dir("readme") })
}

val packageDesktopPortableZip by tasks.registering(Zip::class) {
    // The app folder "TS IPTV" (TS IPTV.exe, app/, runtime/) at the root of the zip, and README.txt.
    from(tasks.named("createReleaseDistributable"))
    from(desktopReleaseReadme)
    archiveFileName.set("TS-IPTV-$desktopVersion-Windows-x64-portable.zip")
    destinationDirectory.set(desktopReleaseStage.map { it.dir("zip") })
    isZip64 = true
}

// Lambdas below take the version from a local: a script-level val would drag the build script
// into the configuration cache, which cannot store it.
// Sync, not Copy: packageDesktopRelease takes this whole folder, so an MSI of an earlier version
// left here would be published again.
val packageDesktopMsi by tasks.registering(Sync::class) {
    val msiName = "TS-IPTV-$desktopVersion-Windows-x64.msi"
    from(tasks.named("packageReleaseMsi"))
    include("*.msi")
    rename { msiName }
    into(desktopReleaseStage.map { it.dir("msi") })
}

val packageDesktopSetupExe by tasks.registering(OneClickSetupExe::class) {
    val version = desktopVersion
    msi.set(desktopReleaseStage.map { it.file("msi/TS-IPTV-$version-Windows-x64.msi") })
    dependsOn(packageDesktopMsi)
    installScript.set(desktopPackaging.file("install.cmd"))
    setupExe.set(desktopReleaseStage.map { it.file("setup/TS-IPTV-$version-Setup.exe") })
}

val desktopReleaseSums by tasks.registering(Sha256Sums::class) {
    files.from(packageDesktopSetupExe.flatMap { it.setupExe }, packageDesktopPortableZip.flatMap { it.archiveFile })
    files.from(packageDesktopSetupExe.flatMap { it.msi })
    sums.set(desktopReleaseStage.map { it.file("sums/SHA256SUMS.txt") })
}

val packageDesktopRelease by tasks.registering(Sync::class) {
    group = "compose desktop"
    description = "Windows release: one-click Setup.exe, MSI, portable zip, README, SHA256SUMS."
    from(packageDesktopSetupExe, packageDesktopPortableZip, packageDesktopMsi, desktopReleaseReadme, desktopReleaseSums)
    into(desktopReleaseDir)
}

room {
    schemaDirectory("$projectDir/schemas")
}
