package tss.t.tsiptv.core.share

interface ShareManager {
    fun shareTextAndImage(title: String, text: String, imageBytes: ByteArray?)
}
