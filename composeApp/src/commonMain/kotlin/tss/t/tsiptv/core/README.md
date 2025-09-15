# TSIPTV Core Utilities

This package contains core utilities for the TSIPTV application, a Kotlin Compose Multiplatform IPTV player.

## Modules

### Network

The network module provides utilities for making network requests.

- `NetworkClient`: Interface for making network requests
- `SimpleNetworkClient`: Simple implementation of NetworkClient

Usage:
```kotlin
val networkClient = SimpleNetworkClient()
val response = networkClient.get("https://example.com/api")
```

### Image Loading

The image loading module provides utilities for loading images from network URLs.

- `NetworkImageLoader`: Interface for loading images from network URLs
- `SimpleNetworkImageLoader`: Simple implementation of NetworkImageLoader

Usage:
```kotlin
val imageLoader = SimpleNetworkImageLoader()

@Composable
fun MyScreen() {
    imageLoader.LoadNetworkImage(
        url = "https://example.com/image.jpg",
        contentDescription = "My Image"
    )
}
```

### Local Storage

The local storage module provides utilities for storing key-value pairs.

- `KeyValueStorage`: Interface for key-value storage
- `InMemoryKeyValueStorage`: Simple in-memory implementation of KeyValueStorage

Usage:
```kotlin
val storage = InMemoryKeyValueStorage()
storage.putString("key", "value")
val value = storage.getString("key")
```

### Database

The database module provides utilities for storing and retrieving IPTV data.

- `IPTVDatabase`: Interface for the IPTV database
- `InMemoryIPTVDatabase`: Simple in-memory implementation of IPTVDatabase

Usage:
```kotlin
val database = InMemoryIPTVDatabase()
database.insertChannel(channel)
val channels = database.getAllChannels()
```

### Firebase

The Firebase module provides utilities for integrating with Firebase services.

- `FirebaseAuth`: Interface for Firebase Authentication
- `InMemoryFirebaseAuth`: Simple in-memory implementation of FirebaseAuth
- `FirebaseFirestore`: Interface for Firebase Firestore
- `InMemoryFirebaseFirestore`: Simple in-memory implementation of FirebaseFirestore
- `FirebaseStorage`: Interface for Firebase Storage
- `InMemoryFirebaseStorage`: Simple in-memory implementation of FirebaseStorage

Usage:
```kotlin
val auth = InMemoryFirebaseAuth()
val user = auth.signInWithEmailAndPassword("user@example.com", "password")

val firestore = InMemoryFirebaseFirestore()
firestore.setDocument("channel", "channel1", mapOf("name" to "Channel 1"))

val storage = InMemoryFirebaseStorage()
storage.uploadFile("images/logo.png", byteArrayOf())
```

### Media Player

The media player module provides utilities for playing media.

- `MediaPlayer`: Interface for a media player
- `SimpleMediaPlayer`: Simple implementation of MediaPlayer

Usage:
```kotlin
val player = SimpleMediaPlayer()
player.setMediaItem(MediaItem(id = "1", name = "Channel 1", url = "https://example.com/stream"))
player.prepare()
player.play()
```

### IPTV Parser

The IPTV parser module provides utilities for parsing IPTV playlists.

- `IPTVParser`: Interface for parsing IPTV playlists
- `M3UParser`: Implementation for M3U format
- `XMLParser`: Implementation for XML format
- `JSONParser`: Implementation for JSON format
- `IPTVParserFactory`: Factory for creating IPTV parsers

Usage:
```kotlin
val content = "..."
val parser = IPTVParserFactory.createParserForContent(content)
val playlist = parser.parse(content)
```

## Platform-Specific Implementations

These core utilities provide interfaces and simple implementations that can be used across all platforms. For production use, you should create platform-specific implementations that use the appropriate platform APIs.

For example:
- Android: Use OkHttp for networking, Coil for image loading, Room for database, etc.
- iOS: Use URLSession for networking, SDWebImage for image loading, SQLite for database, etc.
- Desktop: Use OkHttp for networking, Coil for image loading, SQLite for database, etc.
