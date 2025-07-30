package tss.t.tsiptv.core.parser

import nl.adaptivity.xmlutil.serialization.XML
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import tss.t.tsiptv.core.parser.model.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class XMLTVEPGParserTest {

    private lateinit var epgParser: EPGParser
    private lateinit var content: String
    private lateinit var fileSystem: FileSystem

    @BeforeTest
    fun setup() {
        epgParser = EPGParserFactory.createParser(EPGFormat.XMLTV)
        fileSystem = FileSystem.SYSTEM
        content = fileSystem.read("/Users/tun/Tun/ComposeMultiPlatform/TSIPTV/composeApp/src/commonTest/kotlin/assests/epg.xml".toPath()) {
            readUtf8()
        }
    }

    /**
     * Creates a test XMLTVDocument with realistic data for testing
     */
    private fun createTestXMLTVDocument(): XMLTVDocument {
        // Create test channels
        val testChannels = listOf(
            XMLTVChannel(
                id = "vtv1hd",
                displayName = XMLTVDisplayName(
                    value = "VTV1 HD",
                    lang = "vi"
                ),
                displayNumber = "1",
                icon = XMLTVIcon(
                    src = "https://img.lichphatsong.xyz/logo/vtv1hd.jpg"
                )
            ),
            XMLTVChannel(
                id = "vtv2hd",
                displayName = XMLTVDisplayName(
                    value = "VTV2 HD",
                    lang = "vi"
                ),
                displayNumber = "2",
                icon = XMLTVIcon(
                    src = "https://img.lichphatsong.xyz/logo/vtv2hd.jpg"
                )
            )
        )

        // Create test programmes
        val testProgrammes = listOf(
            XMLTVProgramme(
                channel = "vtv1hd",
                channelNumber = "1",
                start = "20250728120000 +0700",
                stop = "20250728130000 +0700",
                title = XMLTVTitle(
                    value = "Test Program 1",
                    lang = "vi"
                ),
                desc = XMLTVDesc(
                    value = "This is a test program description.",
                    lang = "vi"
                ),
                category = XMLTVCategory(
                    value = "Entertainment",
                    lang = "vi"
                ),
                icon = XMLTVIcon(
                    src = "http://img.lichphatsong.xyz/schedule/vtv1hd/test.webp"
                )
            ),
            XMLTVProgramme(
                channel = "vtv2hd",
                channelNumber = "2",
                start = "20250728130000 +0700",
                stop = "20250728140000 +0700",
                title = XMLTVTitle(
                    value = "Test Program 2",
                    lang = "vi"
                ),
                desc = XMLTVDesc(
                    value = "This is another test program description.",
                    lang = "vi"
                ),
                category = XMLTVCategory(
                    value = "News",
                    lang = "vi"
                )
            )
        )

        // Create the XMLTVDocument
        return XMLTVDocument(
            channel = testChannels,
            programme = testProgrammes,
            date = "28-07-2025",
            generatorInfoName = "Test Generator",
            sourceInfoName = "Test Source",
            sourceInfoUrl = "https://test.example.com"
        )
    }

    @Test
    fun `Valid XMLTV content`() {
        // Generate test XMLTVDocument for testing
        val testXMLTVDocument = createTestXMLTVDocument()
        val testXmlString = XML.encodeToString(XMLTVDocument.serializer(), testXMLTVDocument)
        println("Generated test XML: $testXmlString")

        val parse = epgParser.parse(content)
        assertTrue { parse.isNotEmpty() }
    }

    @Test
    fun `Empty XMLTV content`() {
        // Test with an empty string. Expect an exception or an empty list depending on XML library behavior for empty input.

    }

    @Test
    fun `XMLTV content with no programmes`() {
        // Test with valid XMLTV that has channel information but no <programme> elements. Expect an empty list of IPTVProgram.

    }

    @Test
    fun `XMLTV content with no channels`() {
        // Test with valid XMLTV that has <programme> elements but no <channel> elements. 

        // The `toIPTVProgram()` might rely on channel data, so this could lead to nulls or default values. Ensure graceful handling.

    }

    @Test
    fun `Malformed XML content`() {
        // Test with a string that is not valid XML. Expect an XML parsing exception (e.g., SerializationException from kotlinx.serialization).

    }

    @Test
    fun `XMLTV content with missing required programme attributes`() {
        // Test with <programme> elements that are missing attributes required by `toIPTVProgram()` (e.g., start time, channel ID). 

        // Expect `toIPTVProgram()` to return null for these, and `mapNotNull` to filter them out.

    }

    @Test
    fun `XMLTV content with invalid date time formats in programmes`() {
        // Test with <programme> elements having 'start' or 'stop' attributes with date-time strings that don't match `localDateTimeFormat` or are otherwise unparseable by `parseXMLTVDateTime`. 

        // Expect `toIPTVProgram()` to potentially return null if date parsing fails.

    }

    @Test
    fun `XMLTV content with programmes having only start time`() {
        // Test if `toIPTVProgram()` handles cases where a programme might only have a start time and no stop time (if the schema allows this). 

        // Ensure default behavior or null is handled.

    }

    @Test
    fun `XMLTV content with programmes having only stop time`() {
        // Test if `toIPTVProgram()` handles cases where a programme might only have a stop time and no start time (if the schema allows this).

        // Ensure default behavior or null is handled.

    }

    @Test
    fun `XMLTV with special characters in text fields`() {
        // Test with programme titles, descriptions, etc., containing special XML characters (e.g., &, <, >) or unicode characters. 

        // Ensure they are decoded correctly.

    }

    @Test
    fun `Large XMLTV content`() {
        // Test with a very large XMLTV string (many programmes and channel) to check for performance issues or out-of-memory errors during parsing.

    }

    @Test
    fun `XMLTV content with duplicate programme entries`() {
        // Test how duplicate <programme> elements (e.g., same channel and start time) are handled. Expect them to be parsed as distinct IPTVProgram objects unless `toIPTVProgram` or subsequent logic handles deduplication.

    }

    @Test
    fun `XMLTV content with programmes referencing non existent channels`() {
        // Test with <programme> elements whose 'channel' attribute references a channel ID not defined in the <channel> list. 

        // The behavior of `toIPTVProgram()` in this case needs to be verified (e.g., returns null, uses a default channel name).

    }

    @Test
    fun `XMLTV with varying date time timezone offsets`() {
        // Test `parseXMLTVDateTime` with different valid timezone offsets (e.g., YYYYMMDDHHMMSS +0000, YYYYMMDDHHMMSS -0500) if the `localDateTimeFormat` is made more flexible. 

        // Current format only allows '+0700' or no offset.

    }

    @Test
    fun `XMLTV with date time exactly on DST change`() {
        // Test `parseXMLTVDateTime` with date-times that fall exactly on a Daylight Saving Time transition for the system's current timezone. 

        // Ensure `toInstant(TimeZone.currentSystemDefault())` handles this correctly.

    }

    @Test
    fun `XMLTV content with programmes whose  toIPTVProgram  returns null`() {
        // Test specifically that if `it.toIPTVProgram()` returns null for some programme entries, `mapNotNull` correctly filters these out and does not include them in the final list.

    }

    @Test
    fun `XMLTV with different valid date formats for localDateTimeFormat`() {
        // Test `parseXMLTVDateTime` with date-time strings that strictly adhere to 'YYYYMMDDHHMMSS +0700' and 'YYYYMMDDHHMMSS' (without timezone offset).

        // This verifies the `optional` block in `localDateTimeFormat`.

    }

    @Test
    fun `XMLTV with invalid characters in optional timezone part`() {
        // Test `parseXMLTVDateTime` with date-time strings like 'YYYYMMDDHHMMSS +ABCD' to ensure the `optional` block for timezone does not incorrectly parse invalid offsets and fails gracefully.

    }

}
