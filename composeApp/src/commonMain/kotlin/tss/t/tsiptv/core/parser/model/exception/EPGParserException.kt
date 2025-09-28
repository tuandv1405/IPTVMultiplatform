package tss.t.tsiptv.core.parser.model.exception

/**
 * Exception thrown when parsing EPG data fails.
 *
 * @property message The error message
 */
class EPGParserException(override val message: String) : Exception(message)