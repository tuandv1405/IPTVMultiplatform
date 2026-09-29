package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import tss.t.tsiptv.AppBuildInfo
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.InMemoryIPTVDatabase
import tss.t.tsiptv.core.security.SecretCipher
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.di.getCommonModules
import tss.t.tsiptv.di.getDesktopModules
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The F2 Koin graph resolves (catches a missing definition before QC runs the app). Real files
 * are not touched: the database, key-value storage, cipher and player are replaced.
 */
class StremioKoinGraphTest {
    private object NoopPlayer : MediaPlayer {
        override val playbackState = MutableStateFlow(PlaybackState.IDLE)
        override val currentMedia = MutableStateFlow<MediaItem?>(null)
        override val currentPosition = MutableStateFlow(0L)
        override val duration = MutableStateFlow(0L)
        override val playbackSpeed = MutableStateFlow(1f)
        override val isBuffering = MutableStateFlow(false)
        override val isPlaying = MutableStateFlow(false)
        override val volume = MutableStateFlow(1f)
        override val isMuted = MutableStateFlow(false)
        override val playbackError = MutableStateFlow<PlaybackError?>(null)
        override suspend fun prepare(mediaItem: MediaItem) = Unit
        override suspend fun play() = Unit
        override suspend fun pause() = Unit
        override suspend fun stop() = Unit
        override suspend fun seekTo(positionMs: Long) = Unit
        override suspend fun setPlaybackSpeed(speed: Float) = Unit
        override suspend fun setVolume(volume: Float) = Unit
        override suspend fun setMuted(muted: Boolean) = Unit
        override suspend fun release() = Unit
    }

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun f2DefinitionsResolve() {
        val overrides = module {
            single<IPTVDatabase> { InMemoryIPTVDatabase() }
            single<KeyValueStorage> { InMemoryKeyValueStorage() }
            single<SecretCipher> { FakeCipher() }
            single<MediaPlayer> { NoopPlayer }
        }
        val koin = startKoin {
            allowOverride(true)
            modules(getCommonModules() + getDesktopModules() + overrides)
        }.koin
        koin.get<StremioClient>()
        koin.get<AddonBlocklistFetcher>()
        koin.get<AddonRepository>()
        koin.get<MediaHistoryRepository>()
        koin.get<MediaProgressTracker>()
        assertTrue(AppBuildInfo.VERSION_NAME.isNotBlank())
        assertTrue(StremioClient.userAgentFor(AppBuildInfo.VERSION_NAME).startsWith("TSIPTV/"))
    }
}
