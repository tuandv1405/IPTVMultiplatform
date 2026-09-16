package tss.t.tsiptv.core.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import tss.t.tsiptv.core.firebase.auth.InMemoryFirebaseAuth
import tss.t.tsiptv.core.firebase.firestore.InMemoryFirebaseFirestore
import tss.t.tsiptv.core.firebase.storage.InMemoryFirebaseStorage
import java.io.File
import java.util.Properties

/**
 * Brings Firebase up on the JVM desktop targets.
 *
 * This used to print a line and return. Nothing was initialised, so the first
 * Koin lookup of `Firebase.firestore` threw "Default FirebaseApp is not
 * initialized in this process" and the app died before a window could open —
 * the desktop build had never started successfully.
 *
 * `dev.gitlive:firebase-java-sdk` reimplements the parts of the Firebase Android
 * SDK the gitlive wrappers need, but it cannot discover configuration the way
 * `google-services.json` is discovered on Android, and it has no place to keep
 * state. Both have to be supplied here.
 */
class DesktopFirebaseInitializer {
    companion object {
        // Mirrors composeApp/google-services.json. Kept as constants because the
        // Google Services Gradle plugin does not run for the JVM target, so there
        // is no generated resource to read on desktop.
        private const val PROJECT_ID = "tsiptv-8bdd6"
        private const val APPLICATION_ID = "1:234600934735:android:c8d65b7ef4741dacd4a93a"
        private const val API_KEY = "AIzaSyAink_cGRkOZe6PcxJ7y5DCL7JIwrebCH8"
        private const val STORAGE_BUCKET = "tsiptv-8bdd6.firebasestorage.app"

        @Volatile
        private var initialized = false

        /**
         * Safe to call more than once; only the first call does anything.
         */
        @Synchronized
        fun initialize() {
            if (initialized) return

            FirebasePlatform.initializeFirebasePlatform(FilePersistedPlatform())

            // firebase-java-sdk ships a minimal android.app.Application stub for
            // exactly this: the shared gitlive API still asks for a Context.
            Firebase.initialize(
                context = Application(),
                options = FirebaseOptions(
                    applicationId = APPLICATION_ID,
                    apiKey = API_KEY,
                    projectId = PROJECT_ID,
                    storageBucket = STORAGE_BUCKET,
                )
            )

            initialized = true
        }

        fun provideFirebaseAuth(): IFirebaseAuth = InMemoryFirebaseAuth()

        fun provideFirebaseFirestore(): IFirebaseFirestore = InMemoryFirebaseFirestore()

        fun provideFirebaseStorage(): IFirebaseStorage = InMemoryFirebaseStorage()
    }
}

/**
 * Key-value store the Firebase JVM SDK writes its state through — most
 * importantly the signed-in user's refresh token.
 *
 * Backed by a properties file under the user's home directory rather than a map,
 * so a desktop user stays signed in across restarts, the way they do on Android
 * and iOS.
 */
private class FilePersistedPlatform : FirebasePlatform() {

    private val storeDir = File(System.getProperty("user.home"), ".tsiptv").apply { mkdirs() }
    private val storeFile = File(storeDir, "firebase.properties")

    private val values: Properties = Properties().apply {
        if (storeFile.exists()) {
            runCatching { storeFile.inputStream().use { load(it) } }
                .onFailure { println("[Firebase] could not read $storeFile: ${it.message}") }
        }
    }

    override fun store(key: String, value: String) {
        values.setProperty(key, value)
        flush()
    }

    override fun retrieve(key: String): String? = values.getProperty(key)

    override fun clear(key: String) {
        values.remove(key)
        flush()
    }

    override fun log(msg: String) {
        println("[Firebase] $msg")
    }

    override fun getDatabasePath(name: String): File = File(storeDir, name).apply { mkdirs() }

    private fun flush() {
        runCatching {
            storeFile.outputStream().use { values.store(it, "TS IPTV Firebase state") }
        }.onFailure {
            // Losing persistence costs the user a re-login; it must not take the
            // app down with it.
            println("[Firebase] could not write $storeFile: ${it.message}")
        }
    }
}
