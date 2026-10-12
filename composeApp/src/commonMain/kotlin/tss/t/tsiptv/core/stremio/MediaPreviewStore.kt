package tss.t.tsiptv.core.stremio

/**
 * The catalogue item a detail screen opens with, kept in memory and looked up by type and id.
 *
 * It used to travel in the navigation route as JSON. On iOS, Navigation matches every route
 * against a regex that recurses once per character, so a long route (a Vietnamese title and
 * description, URL-encoded) overflowed the main thread's stack and crashed the app.
 * A preview lost with the process (Android) only means the detail waits for the addon meta.
 */
object MediaPreviewStore {
    private const val MAX_ENTRIES = 64

    private val previews = LinkedHashMap<String, StremioMeta>()

    fun put(type: String, id: String, meta: StremioMeta) {
        val key = key(type, id)
        previews.remove(key)
        previews[key] = meta
        while (previews.size > MAX_ENTRIES) previews.remove(previews.keys.first())
    }

    fun get(type: String, id: String): StremioMeta? = previews[key(type, id)]

    private fun key(type: String, id: String) = "$type|$id"
}
