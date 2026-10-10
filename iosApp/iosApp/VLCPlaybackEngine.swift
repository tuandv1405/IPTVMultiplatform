import Foundation
import MediaPlayer
import MobileVLCKit
import UIKit
import ComposeApp

/// Creates the VLCKit engine for clear MPEG-DASH. Registered in `iOSApp.init()`:
/// `IosPlaybackEngines.shared.dashFactory = VLCPlaybackEngineFactory()`.
final class VLCPlaybackEngineFactory: NSObject, IosPlaybackEngineFactory {
    func create() -> IosPlaybackEngine {
        VLCPlaybackEngine()
    }
}

/// `IosPlaybackEngine` on VLCKit (MobileVLCKit 3.x, LGPL-2.1). libVLC's `adaptive` module plays
/// clear MPEG-DASH (and HLS). The app routes only clear DASH here; AVPlayer keeps HLS and files.
///
/// Rules: everything runs on the main thread (VLCKit calls its delegate on the main thread);
/// never log a URL or a header value (they may carry tokens).
final class VLCPlaybackEngine: NSObject, IosPlaybackEngine, VLCMediaPlayerDelegate {

    /// The render surface. VLC draws into it (`drawable`).
    let view: UIView

    private let player: VLCMediaPlayer
    private var listener: IosPlaybackEngineListener?

    /// A seek asked for before the stream could seek (resume right after opening).
    private var pendingSeekMs: Int64?
    private var lastReportedState: Int32 = -1
    private var nowPlayingTitle: String?
    private var nowPlayingArtist: String?
    private var remoteCommandTargets: [Any] = []

    override init() {
        let surface = UIView(frame: .zero)
        surface.backgroundColor = .black
        surface.isUserInteractionEnabled = false
        view = surface
        // `--network-caching` (ms): a little more than the 1 s default for adaptive streams.
        player = VLCMediaPlayer(options: ["--network-caching=1500"])
        super.init()
        player.delegate = self
        player.drawable = surface
    }

    // MARK: - IosPlaybackEngine

    func attachListener(listener: IosPlaybackEngineListener?) {
        self.listener = listener
    }

    func load(url: String, headers: [String: String], startPositionMs: Int64) {
        pendingSeekMs = nil
        lastReportedState = -1
        guard let mediaURL = URL(string: url) else {
            listener?.onError(code: IosEngineError.shared.STREAM_FAILED, message: "invalid URL")
            return
        }
        let media = VLCMedia(url: mediaURL)
        for option in Self.headerOptions(headers) {
            media.addOption(option)
        }
        if startPositionMs > 0 {
            media.addOption(":start-time=\(Double(startPositionMs) / 1000.0)")
        }
        player.media = media
        report(state: IosEngineState.shared.BUFFERING)
    }

    func play() {
        player.play()
    }

    func pause() {
        if player.canPause {
            player.pause()
        } else {
            // Live streams that cannot pause: stop rather than keep downloading.
            player.stop()
        }
    }

    func seekTo(positionMs: Int64) {
        guard player.isSeekable else {
            pendingSeekMs = positionMs
            return
        }
        player.time = VLCTime(number: NSNumber(value: positionMs))
    }

    func stop() {
        pendingSeekMs = nil
        if player.media != nil {
            player.stop()
        }
        player.media = nil
        clearNowPlaying()
    }

    func dispose() {
        stop()
        removeRemoteCommands()
        player.delegate = nil
        player.drawable = nil
        listener = nil
    }

    func changeVolume(volume: Float) {
        // VLC volume: 0 … 200, 100 = unchanged. The app's volume is 0 … 1.
        let clamped = max(0, min(1, volume))
        player.audio?.volume = Int32((clamped * 100).rounded())
    }

    func changeRate(rate: Float) {
        player.rate = rate
    }

    func updateNowPlaying(title: String, artist: String?, artworkUrl: String?) {
        nowPlayingTitle = title
        nowPlayingArtist = artist
        installRemoteCommandsIfNeeded()
        refreshNowPlaying()
    }

    // MARK: - VLCMediaPlayerDelegate

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch player.state {
        case .opening:
            report(state: IosEngineState.shared.BUFFERING)
        case .buffering:
            // VLC reports buffering repeatedly while playing; only a real stall counts.
            report(state: player.isPlaying ? IosEngineState.shared.PLAYING : IosEngineState.shared.BUFFERING)
        case .playing:
            report(state: IosEngineState.shared.PLAYING)
            applyPendingSeek()
        case .paused:
            report(state: IosEngineState.shared.PAUSED)
        case .ended:
            report(state: IosEngineState.shared.ENDED)
        case .stopped:
            report(state: IosEngineState.shared.IDLE)
        case .error:
            // libVLC does not expose the HTTP status: every failure is a stream failure.
            listener?.onError(code: IosEngineError.shared.STREAM_FAILED, message: "VLC error state")
        case .esAdded:
            break
        @unknown default:
            break
        }
        refreshNowPlaying()
    }

    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        let position = player.time.value?.int64Value ?? 0
        // Live and unknown lengths are 0 (or negative): the app shows LIVE for those.
        let length = player.media?.length.value?.int64Value ?? 0
        listener?.onProgress(positionMs: position, durationMs: length)
        if player.isPlaying {
            applyPendingSeek()
        }
    }

    // MARK: - Helpers

    private func report(state: Int32) {
        guard state != lastReportedState else { return }
        lastReportedState = state
        listener?.onStateChanged(state: state)
    }

    private func applyPendingSeek() {
        guard let target = pendingSeekMs, player.isSeekable else { return }
        pendingSeekMs = nil
        player.time = VLCTime(number: NSNumber(value: target))
    }

    /// Request headers VLC can send, as per-media options. libVLC 3 has options for the
    /// User-Agent and the Referer only: every other header (Authorization, Cookie, Origin,
    /// X-…) is dropped. Values are never logged.
    static func headerOptions(_ headers: [String: String]) -> [String] {
        var options: [String] = []
        for (name, value) in headers {
            switch name.lowercased() {
            case "user-agent":
                options.append(":http-user-agent=\(value)")
            case "referer", "referrer":
                options.append(":http-referrer=\(value)")
            default:
                continue
            }
        }
        return options
    }

    // MARK: - Lock screen / Control Center

    private func refreshNowPlaying() {
        guard let title = nowPlayingTitle else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPNowPlayingInfoPropertyPlaybackRate: player.isPlaying ? Double(player.rate) : 0.0,
        ]
        if let artist = nowPlayingArtist {
            info[MPMediaItemPropertyArtist] = artist
        }
        let lengthMs = player.media?.length.value?.int64Value ?? 0
        if lengthMs > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(lengthMs) / 1000.0
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = Double(player.time.value?.int64Value ?? 0) / 1000.0
        } else {
            info[MPNowPlayingInfoPropertyIsLiveStream] = true
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func clearNowPlaying() {
        nowPlayingTitle = nil
        nowPlayingArtist = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func installRemoteCommandsIfNeeded() {
        guard remoteCommandTargets.isEmpty else { return }
        let center = MPRemoteCommandCenter.shared()
        remoteCommandTargets.append(center.playCommand.addTarget { [weak self] _ in
            guard let self = self, self.player.media != nil else { return .noActionableNowPlayingItem }
            self.player.play()
            return .success
        })
        remoteCommandTargets.append(center.pauseCommand.addTarget { [weak self] _ in
            guard let self = self, self.player.media != nil else { return .noActionableNowPlayingItem }
            self.pause()
            return .success
        })
        remoteCommandTargets.append(center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self = self, self.player.media != nil else { return .noActionableNowPlayingItem }
            if self.player.isPlaying { self.pause() } else { self.player.play() }
            return .success
        })
    }

    private func removeRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        for target in remoteCommandTargets {
            center.playCommand.removeTarget(target)
            center.pauseCommand.removeTarget(target)
            center.togglePlayPauseCommand.removeTarget(target)
        }
        remoteCommandTargets.removeAll()
    }
}
