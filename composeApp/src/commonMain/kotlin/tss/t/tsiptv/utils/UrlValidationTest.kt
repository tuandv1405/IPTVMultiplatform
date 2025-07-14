package tss.t.tsiptv.utils

fun main() {
    // Valid URLs
    val validUrls = listOf(
        "https://m.cnbuy.app/pages/system/down-load",
        "https://m.cnbuy.app/",
        "https://m.cnbuy.app/parse",
        "http://m.cnbuy.app/parse",
        "https://m.cnbuy.app/parse?q=a",
        "http://m.cnbuy.app/parse?q=a"
    )
    
    // Invalid URLs
    val invalidUrls = listOf(
        "https:/m.cnbuy.app/parse",
        "http:/m.cnbuy.app/parse",
        "invalid-url",
        "http:m.cnbuy.app",
        "https:m.cnbuy.app"
    )
    
    println("Testing valid URLs:")
    validUrls.forEach { url ->
        val isValid = url.isValidUrl()
        println("$url -> $isValid (Expected: true)")
        if (!isValid) {
            println("ERROR: URL should be valid but was marked as invalid: $url")
        }
    }
    
    println("\nTesting invalid URLs:")
    invalidUrls.forEach { url ->
        val isValid = url.isValidUrl()
        println("$url -> $isValid (Expected: false)")
        if (isValid) {
            println("ERROR: URL should be invalid but was marked as valid: $url")
        }
    }
    
    // Summary
    val validUrlsCorrect = validUrls.all { it.isValidUrl() }
    val invalidUrlsCorrect = invalidUrls.none { it.isValidUrl() }
    
    println("\nSummary:")
    println("Valid URLs correctly validated: $validUrlsCorrect")
    println("Invalid URLs correctly validated: $invalidUrlsCorrect")
    println("Overall test result: ${validUrlsCorrect && invalidUrlsCorrect}")
}