package tss.t.tsiptv.core.share

class DesktopShareManager : ShareManager {
    override fun shareTextAndImage(title: String, text: String, imageBytes: ByteArray?) {
        // Simple fallback or print to console for desktop
        println("Share Desktop: $title - $text")
    }
}
