package tss.t.tsiptv

interface Platform {
    val name: String
    val isDesktop: Boolean
        get() = false
    val isAndroid: Boolean
        get() = false
    val isIOS: Boolean
        get() = false
}

expect fun getPlatform(): Platform