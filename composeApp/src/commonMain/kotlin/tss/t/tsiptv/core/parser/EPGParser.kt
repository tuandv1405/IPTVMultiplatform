package tss.t.tsiptv.core.parser

/**
 * Parser for Electronic Program Guide (EPG) data.
 * This class is responsible for parsing program schedules from EPG data sources.
 */
interface EPGParser {
    /**
     * Parses EPG data from the given content.
     *
     * @param content The EPG data content as a string
     * @return A list of program schedules
     * @throws EPGParserException if parsing fails
     */
    fun parse(content: String): List<IPTVProgram>

    /**
     * Gets the supported format for this parser.
     *
     * @return The supported format
     */
    fun getSupportedFormat(): EPGFormat
}

/**
 * Enum representing the format of an EPG data source.
 */
enum class EPGFormat {
    XML,
    JSON,
    XMLTV,
    UNKNOWN
}

/**
 * Exception thrown when parsing EPG data fails.
 *
 * @property message The error message
 */
class EPGParserException(override val message: String) : Exception(message)

/**
 * Factory for creating EPG parsers.
 */
object EPGParserFactory {
    /**
     * Creates an EPG parser for the specified format.
     *
     * @param format The format to create a parser for
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParser(format: EPGFormat): EPGParser {
        return when (format) {
            EPGFormat.XML -> XMLEPGParser()
            EPGFormat.JSON -> JSONEPGParser()
            EPGFormat.XMLTV -> XMLTVEPGParser()
            EPGFormat.UNKNOWN -> throw IllegalArgumentException("Unknown EPG format")
        }
    }

    /**
     * Detects the format of EPG data from its content.
     *
     * @param content The EPG data content as a string
     * @return The detected format
     */
    fun detectFormat(content: String): EPGFormat {
        return when {
            content.trimStart().startsWith("<?xml") -> {
                if (content.contains("<tv") || content.contains("<channel") || content.contains("<programme")) {
                    EPGFormat.XMLTV
                } else {
                    EPGFormat.XML
                }
            }
            content.trimStart().startsWith("{") -> EPGFormat.JSON
            else -> EPGFormat.UNKNOWN
        }
    }

    /**
     * Creates an EPG parser for the content.
     *
     * @param content The EPG data content as a string
     * @return The created parser
     * @throws IllegalArgumentException if the format is not supported
     */
    fun createParserForContent(content: String): EPGParser {
        val format = detectFormat(content)
        return createParser(format)
    }
}

/**
 * Implementation of EPGParser for XML format.
 */
class XMLEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        if (!content.trimStart().startsWith("<?xml")) {
            throw EPGParserException("Invalid XML format: missing XML header")
        }

        // Simple implementation for now
        // In a real implementation, we would use a proper XML parser library
        val programs = mutableListOf<IPTVProgram>()

        // TODO: Implement XML parsing logic

        return programs
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XML
    }
}

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

/**
 * Implementation of EPGParser for XMLTV format.
 * XMLTV is a common format for TV listings.
 */
class XMLTVEPGParser : EPGParser {
    override fun parse(content: String): List<IPTVProgram> {
        if (!content.trimStart().startsWith("<?xml") || !content.contains("<tv")) {
            throw EPGParserException("Invalid XMLTV format: missing XML header or <tv> element")
        }

        // Simple implementation for now
        // In a real implementation, we would use a proper XML parser library
        val programs = mutableListOf<IPTVProgram>()

        // Parse channel mappings first (channel id -> channel)
        val channelMap = mutableMapOf<String, String>()

        // Extract channel IDs and names using simple string operations
        val lines = content.lines()
        var currentChannelId: String? = null

        for (line in lines) {
            if (line.contains("<channel") && line.contains("id=")) {
                val idStart = line.indexOf("id=\"") + 4
                val idEnd = line.indexOf("\"", idStart)
                if (idStart > 4 && idEnd > idStart) {
                    currentChannelId = line.substring(idStart, idEnd)
                }
            } else if (line.contains("<display-name>") && line.contains("</display-name>") && currentChannelId != null) {
                val nameStart = line.indexOf("<display-name>") + 14
                val nameEnd = line.indexOf("</display-name>")
                if (nameStart > 14 && nameEnd > nameStart) {
                    val displayName = line.substring(nameStart, nameEnd)
                    channelMap[currentChannelId!!] = displayName
                }
            } else if (line.contains("</channel>")) {
                currentChannelId = null
            } else if (line.contains("<programme") && line.contains("start=") && line.contains("stop=") && line.contains("channel=")) {
                // Extract program information
                var channelId: String? = null
                var startTimeStr: String? = null
                var endTimeStr: String? = null

                val channelStart = line.indexOf("channel=\"") + 9
                val channelEnd = line.indexOf("\"", channelStart)
                if (channelStart > 9 && channelEnd > channelStart) {
                    channelId = line.substring(channelStart, channelEnd)
                }

                val startStart = line.indexOf("start=\"") + 7
                val startEnd = line.indexOf("\"", startStart)
                if (startStart > 7 && startEnd > startStart) {
                    startTimeStr = line.substring(startStart, startEnd)
                }

                val stopStart = line.indexOf("stop=\"") + 6
                val stopEnd = line.indexOf("\"", stopStart)
                if (stopStart > 6 && stopEnd > stopStart) {
                    endTimeStr = line.substring(stopStart, stopEnd)
                }

                if (channelId != null && startTimeStr != null && endTimeStr != null) {
                    val startTime = parseXMLTVDateTime(startTimeStr)
                    val endTime = parseXMLTVDateTime(endTimeStr)

                    if (startTime != null && endTime != null) {
                        // Look for title in the next few lines
                        var title = "Unknown Program"
                        var description: String? = null
                        var category: String? = null

                        // Simple approach: check the next 10 lines for title, description, category
                        val maxLines = minOf(lines.size, lines.indexOf(line) + 10)
                        for (i in lines.indexOf(line) + 1 until maxLines) {
                            val nextLine = lines[i]
                            if (nextLine.contains("<title>") && nextLine.contains("</title>")) {
                                val titleStart = nextLine.indexOf("<title>") + 7
                                val titleEnd = nextLine.indexOf("</title>")
                                if (titleStart > 7 && titleEnd > titleStart) {
                                    title = nextLine.substring(titleStart, titleEnd)
                                }
                            } else if (nextLine.contains("<desc>") && nextLine.contains("</desc>")) {
                                val descStart = nextLine.indexOf("<desc>") + 6
                                val descEnd = nextLine.indexOf("</desc>")
                                if (descStart > 6 && descEnd > descStart) {
                                    description = nextLine.substring(descStart, descEnd)
                                }
                            } else if (nextLine.contains("<category>") && nextLine.contains("</category>")) {
                                val catStart = nextLine.indexOf("<category>") + 10
                                val catEnd = nextLine.indexOf("</category>")
                                if (catStart > 10 && catEnd > catStart) {
                                    category = nextLine.substring(catStart, catEnd)
                                }
                            } else if (nextLine.contains("</programme>")) {
                                break
                            }
                        }

                        val programId = "${channelId}_${startTime}"
                        val program = IPTVProgram(
                            id = programId,
                            channelId = channelId,
                            title = title,
                            description = description,
                            startTime = startTime,
                            endTime = endTime,
                            category = category
                        )
                        programs.add(program)
                    }
                }
            }
        }

        return programs
    }

    override fun getSupportedFormat(): EPGFormat {
        return EPGFormat.XMLTV
    }

    /**
     * Parses a date-time string in XMLTV format into a timestamp.
     * XMLTV format is typically YYYYMMDDHHMMSS +/-HHMM
     *
     * @param dateTime The date-time string
     * @return The timestamp in milliseconds since epoch, or null if parsing fails
     */
    private fun parseXMLTVDateTime(dateTime: String): Long? {
        // Example: "20230101120000 +0000"
        val regex = "(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})\\s*([+-]\\d{4})?".toRegex()
        val match = regex.find(dateTime) ?: return null

        val (year, month, day, hour, minute, second) = match.destructured

        return try {
            // Simple conversion to timestamp (not handling time zones properly)
            // Using a simple calculation instead of Calendar for platform independence
            val yearInt = year.toInt()
            val monthInt = month.toInt()
            val dayInt = day.toInt()
            val hourInt = hour.toInt()
            val minuteInt = minute.toInt()
            val secondInt = second.toInt()

            // Simplified timestamp calculation (approximate)
            val secondsSinceEpoch = (yearInt - 1970) * 365 * 24 * 60 * 60 +  // Years (ignoring leap years)
                    monthInt * 30 * 24 * 60 * 60 +  // Months (approximating to 30 days)
                    dayInt * 24 * 60 * 60 +  // Days
                    hourInt * 60 * 60 +  // Hours
                    minuteInt * 60 +  // Minutes
                    secondInt  // Seconds

            secondsSinceEpoch * 1000L  // Convert to milliseconds
        } catch (e: Exception) {
            null
        }
    }
}
