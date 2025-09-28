package tss.t.tsiptv.core.parser.epg

import tss.t.tsiptv.core.parser.EPGFormat
import tss.t.tsiptv.core.parser.EPGParser
import tss.t.tsiptv.core.parser.model.exception.EPGParserException
import tss.t.tsiptv.core.parser.model.IPTVProgram

/**
 * Implementation of EPGParser for JSON format.
 */
class JSONEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        if (!content.trimStart().startsWith("{")) {
            throw EPGParserException("Invalid JSON format: missing opening brace")
        }

        // Simple implementation for now
        // In a real implementation, we would use a proper JSON parser library
        val programs = mutableListOf<IPTVProgram>()

        // TODO: Implement JSON parsing logic

        return programs
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.JSON
    }
}