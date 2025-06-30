package tss.t.tsiptv

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isDesktop: Boolean
        get() = true
}

actual fun getPlatform(): Platform = JVMPlatform()
