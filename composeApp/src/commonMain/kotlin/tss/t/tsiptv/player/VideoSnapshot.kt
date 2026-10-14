package tss.t.tsiptv.player

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A copy of the picture on screen, for the share card. [aspect] is the display aspect ratio, which
 * differs from the pixel size for anamorphic streams (720×576 shown as 16:9).
 */
class VideoSnapshot(val image: ImageBitmap, val aspect: Float)
