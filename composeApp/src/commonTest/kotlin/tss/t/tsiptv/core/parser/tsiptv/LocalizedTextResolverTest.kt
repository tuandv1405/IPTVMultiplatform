package tss.t.tsiptv.core.parser.tsiptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Spec §5.2 resolution order (AC-T7). */
class LocalizedTextResolverTest {

    private val movies = LocalizedText(linkedMapOf("en" to "Movies", "vi" to "Phim", "zh-CN" to "电影"), defaultLanguage = "en")

    @Test
    fun exactUiLanguage() {
        assertEquals("Phim", movies.resolve("vi"))
        assertEquals("电影", movies.resolve("zh-CN"))
        assertEquals("电影", movies.resolve("ZH-cn"))
    }

    @Test
    fun uiLanguageWithoutAKeyFallsBackToMetaLanguage() {
        assertEquals("Movies", movies.resolve("de"))
        val viDefault = LocalizedText(linkedMapOf("fr" to "Films", "vi" to "Phim"), defaultLanguage = "vi")
        assertEquals("Phim", viDefault.resolve("de"))
    }

    @Test
    fun primarySubtagThenSameFamily() {
        // zh-TW UI matches the zh-CN key when there is no zh-TW key.
        assertEquals("电影", movies.resolve("zh-TW"))
        val withPrimary = LocalizedText(linkedMapOf("zh-TW" to "電影", "zh" to "电影 (zh)"), defaultLanguage = "en")
        assertEquals("电影 (zh)", withPrimary.resolve("zh-HK"), "the bare primary subtag wins over a sibling region")
        val regions = LocalizedText(linkedMapOf("en" to "Movies", "pt-PT" to "Filmes PT", "pt-BR" to "Filmes BR"), defaultLanguage = "en")
        assertEquals("Filmes BR", regions.resolve("pt-BR"))
        assertEquals("Filmes PT", regions.resolve("pt"), "first key of the family in document order")
        assertEquals("Filmes PT", regions.resolve("pt-AO"))
    }

    @Test
    fun englishThenFirstEntry() {
        val noDefault = LocalizedText(linkedMapOf("fr" to "Films", "en" to "Movies"), defaultLanguage = "ja")
        assertEquals("Movies", noDefault.resolve("de"))
        val neither = LocalizedText(linkedMapOf("fr" to "Films", "es" to "Películas"), defaultLanguage = "ja")
        assertEquals("Films", neither.resolve("de"))
        assertEquals("Films", neither.resolve(null))
        assertEquals("Films", neither.resolve(""))
    }

    @Test
    fun platformTagSpellings() {
        assertEquals("zh-CN", LocalizedTextResolver.normalizeTag("zh-rCN"))
        assertEquals("zh-CN", LocalizedTextResolver.normalizeTag("zh_CN"))
        assertEquals("pt-BR", LocalizedTextResolver.normalizeTag(" pt-BR "))
        assertEquals("电影", movies.resolve("zh-rCN"))
        assertEquals("电影", movies.resolve("zh_CN"))
    }

    @Test
    fun plainStringsLiveInMetaLanguage() {
        val plain = LocalizedText.plain("Tin tức", "vi")
        assertEquals("Tin tức", plain.resolve("en"))
        assertEquals("Tin tức", plain.resolve("vi"))
        assertNull(LocalizedTextResolver.resolve(emptyMap(), "en", "en"))
    }

    @Test
    fun parsedTextsResolveAgainstTheirDocumentLanguage() {
        val (doc, _) = TsiptvFixtures.success(
            TsiptvFixtures.doc(
                """"channels":[{"id":"a","name":{"fr":"Actualités","vi":"Tin tức"},"url":"https://cdn.example.com/a.m3u8"}]""",
                meta = """{"name":"Nguồn","language":"vi"}""",
            )
        )
        val name = doc.channels.single().name
        assertEquals("vi", name.defaultLanguage)
        assertEquals("Tin tức", name.resolve("de"))
        assertEquals("Actualités", name.resolve("fr-CA"))
    }
}
