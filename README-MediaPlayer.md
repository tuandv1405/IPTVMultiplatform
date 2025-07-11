# Cross-Platform Media Player Implementation

This document provides an overview of the cross-platform media player implementation for Android, iOS, and desktop platforms.

## Architecture

The media player implementation follows a common interface pattern with platform-specific implementations:

1. **Common Interface**: `MediaPlayer` defines the common API for all platforms
2. **Platform-Specific Implementations**:
   - Android: Uses Media3 ExoPlayer with a foreground service
   - iOS: Uses a simplified mock implementation (can be extended to use AVPlayer)
   - Desktop: Uses VLCj for media playback

3. **Factory Pattern**: `MediaPlayerFactory` provides platform-specific implementations

## Key Components

### Common Components

- `MediaPlayer`: Interface defining common functionality
- `MediaItem`: Data class representing media to be played
- `PlaybackState`: Enum representing player states
- `MediaPlayerFactory`: Factory for creating platform-specific implementations
- `MediaPlayerView`: Composable for displaying media with controls

### Android-Specific Components

- `AndroidMediaPlayer`: Android implementation using Media3
- `MediaPlayerService`: Foreground service for background playback
- Android-specific `MediaPlayerContent` composable

### iOS-Specific Components

- `IOSMediaPlayer`: iOS implementation (currently a mock)
- iOS-specific `MediaPlayerContent` composable

### Desktop-Specific Components

- `DesktopMediaPlayer`: Desktop implementation using VLCj
- Desktop-specific `MediaPlayerContent` composable

## Usage

### Basic Usage

```kotlin
// In a composable function
val coroutineScope = rememberCoroutineScope()

// Create a MediaItem
val mediaItem = MediaItem(
    id = "unique_id",
    uri = "https://example.com/video.mp4",
    title = "Sample Video",
    artist = "Sample Artist"
)

// Use the MediaPlayerView composable
MediaPlayerView(
    mediaItem = mediaItem,
    coroutineScope = coroutineScope,
    modifier = Modifier.fillMaxSize()
)
```

### Advanced Usage

For more control, you can create and manage the player directly:

```kotlin
// In a composable function
val coroutineScope = rememberCoroutineScope()
val player = remember { MediaPlayerFactory.createPlayer(coroutineScope) }

// Observe player state
val playbackState by player.playbackState.collectAsState()
val currentPosition by player.currentPosition.collectAsState()
val duration by player.duration.collectAsState()

// Control playback
LaunchedEffect(Unit) {
    player.prepare(mediaItem)
    player.play()
}

// Clean up
DisposableEffect(Unit) {
    onDispose {
        coroutineScope.launch {
            player.release()
        }
    }
}
```

## Platform-Specific Notes

### Android

- Uses Media3 ExoPlayer with a foreground service
- Requires the following permissions in the manifest:
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- Service declaration in the manifest:
  ```xml
  <service
      android:name=".player.service.MediaPlayerService"
      android:foregroundServiceType="mediaPlayback"
      android:exported="false" />
  ```

### iOS

- Currently uses a simplified mock implementation
- Can be extended to use AVPlayer for full functionality

### Desktop

- Uses VLCj for media playback
- Requires VLC to be installed on the system

## Future Improvements

1. Implement full AVPlayer support for iOS
2. Add support for playlists
3. Improve error handling and recovery
4. Add support for subtitles and audio tracks
5. Implement picture-in-picture mode for Android and iOS