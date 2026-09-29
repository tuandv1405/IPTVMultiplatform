package tss.t.tsiptv.core.stremio

/**
 * A catalog request ready to be sent: [extra] is already in manifest declaration order,
 * multi-value extras repeat the key.
 */
data class CatalogRequest(
    val type: String,
    val catalogId: String,
    val extra: List<Pair<String, String>> = emptyList(),
) {
    fun url(transport: StremioTransport): String =
        transport.resourceUrl(StremioResource.CATALOG, type, catalogId, extra)
}

/**
 * Which addon gets which request, as stremio-core's `Manifest::is_resource_supported` (research §2.2,
 * PRD §3). Pure functions over a manifest; adult/enabled filtering is the repository's job.
 */
object ResourceMatcher {

    /**
     * meta / stream / subtitles (any non-catalog resource): find the entry by name. The string form
     * uses the manifest's `types`/`idPrefixes`; the object form uses its own `types` (absent = none)
     * and `idPrefixes` (absent/empty = any id).
     */
    fun supportsResource(manifest: StremioManifest, resource: String, type: String, id: String): Boolean =
        manifest.resourceList.any { entry ->
            if (entry.name != resource) return@any false
            val types = if (entry.isShortForm) manifest.types else entry.types
            val prefixes = if (entry.isShortForm) manifest.idPrefixes else entry.idPrefixes
            types.orEmpty().contains(type) && idMatches(prefixes, id)
        }

    fun supportsMeta(manifest: StremioManifest, type: String, id: String): Boolean =
        supportsResource(manifest, StremioResource.META, type, id)

    fun supportsStream(manifest: StremioManifest, type: String, id: String): Boolean =
        supportsResource(manifest, StremioResource.STREAM, type, id)

    fun supportsSubtitles(manifest: StremioManifest, type: String, id: String): Boolean =
        supportsResource(manifest, StremioResource.SUBTITLES, type, id)

    private fun idMatches(prefixes: List<String>?, id: String): Boolean =
        prefixes.isNullOrEmpty() || prefixes.any { id.startsWith(it) }

    /**
     * Whether the addon declares a resource that can match anything at all (for the preview's
     * "provides streams" line): the entry exists and has at least one type.
     */
    fun declaresResource(manifest: StremioManifest, resource: String): Boolean {
        if (resource == StremioResource.CATALOG) return hasCatalogs(manifest)
        return manifest.resourceList.any { entry ->
            entry.name == resource && (if (entry.isShortForm) manifest.types else entry.types).orEmpty().isNotEmpty()
        }
    }

    /** A non-empty `catalogs` implies the catalog resource, like the SDK does. */
    fun hasCatalogs(manifest: StremioManifest): Boolean = manifest.catalogList.isNotEmpty()

    fun findCatalog(manifest: StremioManifest, type: String, catalogId: String): ManifestCatalog? =
        manifest.catalogList.firstOrNull { it.type == type && it.id == catalogId }

    /**
     * catalog: the `(type, id)` exists, every sent extra is declared and every required extra is
     * present. `resources` and `idPrefixes` do not gate catalogs.
     */
    fun supportsCatalog(
        manifest: StremioManifest,
        type: String,
        catalogId: String,
        extra: List<Pair<String, String>> = emptyList(),
    ): Boolean {
        val catalog = findCatalog(manifest, type, catalogId) ?: return false
        return extraIsValid(catalog, extra)
    }

    fun supportsAddonCatalog(manifest: StremioManifest, type: String, id: String): Boolean =
        manifest.addonCatalogList.any { it.type == type && it.id == id }

    fun extraIsValid(catalog: ManifestCatalog, extra: List<Pair<String, String>>): Boolean {
        val sent = extra.map { it.first }.toSet()
        if (sent.any { catalog.extra(it) == null }) return false
        return catalog.requiredExtras.all { it.name in sent }
    }

    /**
     * Builds the ordered `extra` list for [catalog]: extras in manifest declaration order, each
     * selection limited to `optionsLimit` values (repeated key), undeclared names dropped; `skip`
     * only when declared and > 0, and always **last** (core appends it with `extend_one`).
     * Returns null when a required extra has no value.
     */
    fun buildExtra(
        catalog: ManifestCatalog,
        selections: Map<String, List<String>> = emptyMap(),
        skip: Int? = null,
    ): List<Pair<String, String>>? {
        val out = ArrayList<Pair<String, String>>()
        for (extra in catalog.extras) {
            if (extra.name == StremioExtra.SKIP) continue
            val values = selections[extra.name].orEmpty().filter { it.isNotEmpty() }.distinct().take(extra.optionsLimit)
            if (values.isEmpty() && extra.isRequired) return null
            values.forEach { out += extra.name to it }
        }
        if (catalog.supportsSkip && skip != null && skip > 0) out += StremioExtra.SKIP to skip.toString()
        return out
    }

    /**
     * Default values for required extras: the first option (core `default_required_extra`), or null
     * when a required extra has no options (e.g. a search-only catalog or Cinemeta's `lastVideosIds`).
     */
    fun defaultRequiredSelections(catalog: ManifestCatalog): Map<String, List<String>>? {
        val out = LinkedHashMap<String, List<String>>()
        for (extra in catalog.requiredExtras) {
            if (extra.name == StremioExtra.SKIP) continue
            val first = extra.options.firstOrNull() ?: return null
            out[extra.name] = listOf(first)
        }
        return out
    }

    /**
     * Home/board rows: catalogs with no required extra, plus catalogs whose required extras all
     * have options (filled with the first option). Manifest order.
     */
    fun boardCatalogs(manifest: StremioManifest): List<CatalogRequest> =
        manifest.catalogList.mapNotNull { catalog ->
            val defaults = defaultRequiredSelections(catalog) ?: return@mapNotNull null
            val extra = buildExtra(catalog, defaults) ?: return@mapNotNull null
            CatalogRequest(catalog.type, catalog.id, extra)
        }

    /**
     * Search fan-out: every catalog that declares `search` (required or not). Other required extras
     * get their first option; a catalog with another required extra without options is skipped.
     */
    fun searchCatalogs(manifest: StremioManifest, query: String): List<CatalogRequest> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return manifest.catalogList.mapNotNull { catalog ->
            if (!catalog.supportsSearch) return@mapNotNull null
            val selections = LinkedHashMap<String, List<String>>()
            for (extra in catalog.requiredExtras) {
                if (extra.name == StremioExtra.SEARCH || extra.name == StremioExtra.SKIP) continue
                selections[extra.name] = listOf(extra.options.firstOrNull() ?: return@mapNotNull null)
            }
            selections[StremioExtra.SEARCH] = listOf(q)
            val extra = buildExtra(catalog, selections) ?: return@mapNotNull null
            CatalogRequest(catalog.type, catalog.id, extra)
        }
    }

    /**
     * Types present in the board catalogs, in the order movie, series, tv, channel, then the others
     * alphabetically (PRD §5 type tabs).
     */
    fun orderTypes(types: Collection<String>): List<String> {
        val known = listOf(StremioType.MOVIE, StremioType.SERIES, StremioType.TV, StremioType.CHANNEL)
        val distinct = types.toSet()
        return known.filter { it in distinct } + (distinct - known.toSet()).sorted()
    }
}
