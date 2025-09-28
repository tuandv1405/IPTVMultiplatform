package tss.t.tsiptv.core.parser.model.exception

/**
 * Exception thrown when parsing an IPTV playlist fails.
 *
 * @property message The error message
 */
class IPTVParserException(override val message: String) : Exception(message)