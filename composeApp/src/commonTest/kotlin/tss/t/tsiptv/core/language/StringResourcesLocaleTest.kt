package tss.t.tsiptv.core.language

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every string exists in every shipped locale (roadmap §2 item 4). A key missing from one file
 * falls back to English at run time, which is easy to miss in review and in QC.
 */
class StringResourcesLocaleTest {

    private val locales = listOf("values", "values-vi", "values-de", "values-es", "values-fr", "values-ja", "values-zh-rCN")

    private val resourcesDir: Path by lazy {
        val fs = FileSystem.SYSTEM
        var dir: Path? = fs.canonicalize(".".toPath())
        while (dir != null) {
            for (candidate in listOf("composeApp/src/commonMain/composeResources", "src/commonMain/composeResources")) {
                val resolved = dir.div(candidate)
                if (fs.exists(resolved)) return@lazy resolved
            }
            dir = dir.parent
        }
        error("composeResources not found")
    }

    /** Every `strings*.xml` of the locale (features may keep their strings in their own file). */
    private fun text(locale: String): String {
        val fs = FileSystem.SYSTEM
        return fs.list(resourcesDir.div(locale))
            .filter { it.name.startsWith("strings") && it.name.endsWith(".xml") }
            .sortedBy { it.name }
            .joinToString("\n") { file -> fs.read(file) { readUtf8() } }
    }

    private fun keys(locale: String): Set<String> =
        STRING_NAME.findAll(text(locale)).map { it.groupValues[1] }.toSet()

    /**
     * Compose resources show `\'` and `\"` literally (unlike Android resources), so users saw
     * "This channel\'s DRM…". Use plain or typographic quotes instead.
     */
    @Test
    fun noBackslashEscapedQuotes() {
        for (locale in locales) {
            val bad = text(locale).lines().filter { "\\'" in it || "\\\"" in it }
            assertEquals(emptyList(), bad, locale)
        }
    }

    @Test
    fun pluralsHaveAnOtherForm() {
        for (locale in locales) {
            PLURALS.findAll(text(locale)).forEach { match ->
                assertTrue("quantity=\"other\"" in match.value, "$locale ${match.groupValues[1]}")
            }
        }
    }

    @Test
    fun everyLocaleHasTheSameKeys() {
        val reference = keys("values")
        assertTrue(reference.isNotEmpty())
        for (locale in locales.drop(1)) {
            val actual = keys(locale)
            val missing = reference - actual
            val extra = actual - reference
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                fail("$locale: missing $missing, not in English $extra")
            }
        }
    }

    @Test
    fun f1KeysArePresent() {
        val required = setOf(
            "import_from_file_title", "import_file_unavailable_tv", "import_error_file_too_large",
            "import_error_html_page", "import_error_unknown_format", "import_error_needs_update",
            "import_replace_existing", "import_summary_skipped", "import_details_title",
            "import_details_action", "import_skipped_kodi_addon", "import_skipped_web_scraping",
            "import_skipped_no_url", "import_hls_single_title", "import_hls_single_message",
            "import_hls_single_action", "strm_error_kodi_addon", "playlist_file_refresh_hint",
            "channel_badge_radio", "drm_not_supported_device", "drm_not_supported_config",
            "drm_license_failed", "stream_error_forbidden_headers", "stream_error_generic",
            "import_error_file_read", "import_replace_title",
        )
        for (locale in locales) {
            assertEquals(emptySet(), required - keys(locale), locale)
        }
    }

    /** AC-S25: every F2 key of the PRD's string table exists in all seven locales. */
    @Test
    fun f2KeysArePresent() {
        val required = setOf(
            "addons_title", "addons_empty", "addon_add", "addon_url_label", "addon_notice", "addon_added_by_you",
            "addon_added", "addon_warn_http", "addon_warn_p2p", "addon_warn_adult", "addon_confirm_adult",
            "addon_error_not_manifest", "addon_error_legacy", "addon_error_local_server",
            "addon_error_invalid_manifest", "addon_error_unreachable", "addon_needs_configuration",
            "addon_configure", "addon_replace_existing", "addon_blocked", "addon_updated", "addon_update_now",
            "addon_remove_confirm", "addon_move_up", "addon_move_down", "addon_status_ok",
            "addon_status_unreachable", "addon_status_blocked", "addon_partial_failure", "discover_title",
            "discover_search_hint", "discover_no_results", "discover_empty_catalog", "discover_offline",
            "discover_see_all", "discover_type_all", "type_movie", "type_series", "type_tv", "type_channel",
            "continue_watching_media", "history_media_section", "meta_play", "meta_rating", "meta_season",
            "meta_specials", "meta_upcoming", "meta_not_found", "stream_picker_title", "stream_show_unsupported",
            "stream_unsupported", "stream_none_playable", "stream_open_external", "stream_external_tv",
            "stream_badge_region", "stream_badge_may_not_play",
        )
        for (locale in locales) {
            assertEquals(emptySet(), required - keys(locale), locale)
        }
    }

    /** F3: the 22 `source_issue_*` keys (one per validation code) and the other PRD strings, in all seven locales. */
    @Test
    fun f3KeysArePresent() {
        val issues = tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.entries.map { "source_issue_" + it.name.lowercase() }.toSet()
        assertEquals(22, issues.size)
        val required = issues + setOf(
            "source_preview_title", "source_by_author", "source_counts", "source_counts_extra", "source_hosts_title",
            "source_notice", "source_warn_adult", "source_warn_adult_include", "source_import", "source_items_skipped",
            "source_fetching_includes", "source_error_title", "source_error_not_json", "source_error_not_source",
            "source_error_version", "source_error_too_large", "source_error_missing_fields", "source_error_empty",
            "source_error_nothing_loaded", "source_replace_existing", "source_home_tab", "source_channels_tab",
            "source_about", "source_refresh", "source_remove", "source_remove_confirm", "source_open_homepage",
            "source_include_ok", "source_include_stale", "source_include_failed", "source_include_withheld",
            "source_section_continue", "source_section_movies", "source_section_series", "source_section_radio",
            "source_section_channels", "source_search_hint", "addons_from_sources", "source_stream_default",
            "source_import_done",
        )
        for (locale in locales) {
            assertEquals(emptySet(), required - keys(locale), locale)
        }
    }

    /** AC-X1: TV cast, send to TV, device limit and sync (docs/prd-tv-cast-and-sync.md §9). */
    @Test
    fun castSyncKeysArePresent() {
        val required = setOf(
            "connect_title", "lan_cast_title", "lan_conditions_title", "lan_conditions_body", "lan_connect_by_ip",
            "lan_pair_enter_code", "lan_pair_wrong_code", "lan_tv_pair_title", "lan_tv_pair_message",
            "lan_tv_offer_title", "lan_tv_offer_message", "lan_tv_accept", "lan_tv_decline", "lan_tv_receive_title",
            "send_tv_action", "send_tv_remaining", "send_tv_quota_reached", "send_tv_tasks", "send_tv_task_rewarded",
            "devices_limit_title", "devices_limit_message", "devices_sign_out_remote", "devices_cancel_sign_in",
            "devices_removed_notice", "sync_title", "sync_new_available", "sync_merge", "sync_replace",
            "sync_replace_confirm_message", "sync_task_rewarded",
        )
        for (locale in locales) {
            assertEquals(emptySet(), required - keys(locale), locale)
        }
    }

    /** AC-SUB16: subscriptions (docs/prd-subscriptions.md §3). */
    @Test
    fun subscriptionKeysArePresent() {
        val required = setOf(
            "plans_title", "plans_noads_name", "plans_unlimited_name", "plans_disclosure", "plans_terms", "plans_privacy",
            "plans_manage", "plans_restore", "plans_trial", "plans_price_month", "plans_price_year",
            "plans_status_payment_issue", "plans_not_available", "plans_msg_pending", "remove_ads_link",
            "quota_upgrade_unlimited", "send_tv_unlimited", "sync_unlimited",
        )
        for (locale in locales) {
            assertEquals(emptySet(), required - keys(locale), locale)
        }
    }

    private companion object {
        val STRING_NAME = Regex("<(?:string|plurals)\\s+name=\"([^\"]+)\"")
        val PLURALS = Regex("<plurals\\s+name=\"([^\"]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
    }
}
