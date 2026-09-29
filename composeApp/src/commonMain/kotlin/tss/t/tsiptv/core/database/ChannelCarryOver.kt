package tss.t.tsiptv.core.database

/**
 * Which stored channel each freshly parsed channel continues, so favourites, last-watched
 * times and history follow it when its id changes (the v4 re-parse, or a playlist whose
 * duplicate `tvg-id`s were numbered differently before).
 *
 * The id is the identity; a URL is only supporting evidence, because providers rotate tokens
 * and reuse one placeholder link for many channels. Passes, each stored row used at most once:
 *
 * 1. same id and same URL;
 * 2. same URL where the new id is a `~N` sibling of the stored id (duplicate `tvg-id`s that
 *    were numbered in a different order before);
 * 3. same id;
 * 4. same URL, only when the stored id is gone from the new list and the URL is unique on
 *    both sides (a channel renamed by the new parser rules);
 * 5. the pre-F1 id ([NewChannel.legacyId]).
 *
 * @return new id to the stored id it continues; entries only where one was found
 */
internal fun matchPreviousChannels(previous: List<StoredChannel>, fresh: List<NewChannel>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val unused = previous.toMutableList()
    val freshIds = fresh.map { it.id }.toSet()
    val storedUrlCount = previous.groupingBy { it.url }.eachCount()
    val freshUrlCount = fresh.groupingBy { it.url }.eachCount()

    fun pass(match: (NewChannel, StoredChannel) -> Boolean) {
        for (n in fresh) {
            if (n.id in result) continue
            val index = unused.indexOfFirst { match(n, it) }
            if (index >= 0) result[n.id] = unused.removeAt(index).id
        }
    }

    pass { n, s -> s.id == n.id && s.url == n.url }
    pass { n, s -> s.url == n.url && n.id != s.id && baseId(n.id) == baseId(s.id) }
    pass { n, s -> s.id == n.id }
    pass { n, s ->
        s.url == n.url && s.id !in freshIds &&
                storedUrlCount[s.url] == 1 && freshUrlCount[n.url] == 1
    }
    pass { n, s -> n.legacyId != null && s.id == n.legacyId }
    return result
}

private val SIBLING_SUFFIX = Regex("~\\d+$")
private fun baseId(id: String) = id.replace(SIBLING_SUFFIX, "")

internal data class StoredChannel(val id: String, val url: String)
internal data class NewChannel(val id: String, val url: String, val legacyId: String?)
