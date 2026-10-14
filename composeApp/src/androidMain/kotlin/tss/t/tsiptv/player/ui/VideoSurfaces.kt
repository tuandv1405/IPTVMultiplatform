package tss.t.tsiptv.player.ui

import android.view.SurfaceView
import androidx.annotation.MainThread
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference

/**
 * The player views on screen (player screen, mini player, TV), newest last, so the player can
 * read back the picture for the share card: ExoPlayer itself never holds the pixels.
 */
@MainThread
internal object VideoSurfaces {
    private val views = mutableListOf<WeakReference<PlayerView>>()

    fun attach(view: PlayerView) {
        views.removeAll { it.get() == null || it.get() === view }
        views += WeakReference(view)
    }

    fun detach(view: PlayerView) {
        views.removeAll { it.get() == null || it.get() === view }
    }

    /** The newest surface that is on screen and has a picture to read. */
    fun current(): SurfaceView? = views.asReversed().firstNotNullOfOrNull { ref ->
        (ref.get()?.videoSurfaceView as? SurfaceView)?.takeIf {
            it.isAttachedToWindow && it.width > 0 && it.height > 0 && it.holder.surface?.isValid == true
        }
    }
}
