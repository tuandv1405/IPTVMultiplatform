package tss.t.tsiptv

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * Locates the files under `composeApp/src/commonTest/kotlin/assests`.
 *
 * The working directory differs between run configurations (Gradle uses the module directory,
 * IDE run configurations often use the repository root), so the directory is discovered by
 * walking up from the working directory instead of being hard-coded to one machine's checkout.
 */
object TestAssets {
    private val RELATIVE_CANDIDATES = listOf(
        "composeApp/src/commonTest/kotlin/assests",
        "src/commonTest/kotlin/assests"
    )

    private val assetsDir: Path by lazy {
        val fileSystem = FileSystem.SYSTEM
        var dir: Path? = fileSystem.canonicalize(".".toPath())
        while (dir != null) {
            for (candidate in RELATIVE_CANDIDATES) {
                val resolved = dir.div(candidate)
                if (fileSystem.exists(resolved)) return@lazy resolved
            }
            dir = dir.parent
        }
        error("Could not locate the commonTest assets directory from ${fileSystem.canonicalize(".".toPath())}")
    }

    fun path(fileName: String): Path = assetsDir.div(fileName)

    fun read(fileName: String): String = FileSystem.SYSTEM.read(path(fileName)) { readUtf8() }
}
