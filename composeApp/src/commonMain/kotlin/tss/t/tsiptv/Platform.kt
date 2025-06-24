package tss.t.tsiptv

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform