package tss.t.tsiptv.utils

/**
 * Formats a Long price value with Vietnamese currency symbol "đ"
 * @return Formatted price string with Vietnamese currency unit
 */
fun Long?.formatVietnamCurrency(): String {
    return if (this != null) {
        "${this}đ"
    } else {
        ""
    }
}

/**
 * Formats a Long price value with Vietnamese currency symbol "đ" and thousand separators
 * @return Formatted price string with Vietnamese currency unit and proper formatting
 */
fun Long?.formatVietnamCurrencyWithSeparator(): String {
    return if (this != null) {
        val formattedNumber = this.toString().reversed().chunked(3).joinToString(".").reversed()
        "${formattedNumber}đ"
    } else {
        ""
    }
}
