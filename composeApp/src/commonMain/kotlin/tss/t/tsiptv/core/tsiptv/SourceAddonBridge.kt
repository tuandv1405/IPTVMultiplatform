package tss.t.tsiptv.core.tsiptv

/**
 * How TS IPTV Source `stremio` includes reach F2's addon layer (PRD §5 `stremio`): the manifest
 * is fetched and checked (blocklist, validity) at resolve time, but nothing is installed until
 * the source is stored, so a cancelled import leaves no addon behind.
 */
interface SourceAddonBridge {
    /** Fetches and validates the manifest at [manifestUrl]; never throws for network or addon errors. */
    suspend fun probe(manifestUrl: String): AddonProbe

    /**
     * Installs (or keeps) the probed addon as owned by the source playlist [ownerPlaylistId]. An
     * addon the user added themselves stays theirs (it is only reused).
     * @return the addon id
     */
    suspend fun install(probe: AddonProbe.Ready, ownerPlaylistId: String): String

    /** Removes every addon owned by [ownerPlaylistId] except [keep] (and their Stremio history). */
    suspend fun removeOwned(ownerPlaylistId: String, keep: Set<String> = emptySet())

    /** Addon ids [ownerPlaylistId] owns now (to undo an install when the store fails). */
    suspend fun ownedBy(ownerPlaylistId: String): Set<String> = emptySet()
}

sealed interface AddonProbe {
    /**
     * @property handle Opaque object the bridge needs to install (F2's `AddonPreview`)
     * @property manifestJson The raw manifest (catalogues for `W_QUERY_REF`, counted in the budget)
     */
    class Ready(
        val addonId: String,
        val isAdult: Boolean,
        val manifestJson: String,
        val handle: Any,
    ) : AddonProbe {
        override fun toString(): String = "Ready(addonId=$addonId, adult=$isAdult)"
    }

    /** Short code, never a URL: `blocked`, `unreachable`, `invalid_manifest`, `needs_configuration`, `invalid_url`. */
    data class Failed(val code: String) : AddonProbe
}
