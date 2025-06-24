# TSIPTV - Kotlin Compose Multiplatform IPTV Player

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop. It's an IPTV player application that can parse IPTV links from URLs in M3U format and save them to a local database.

## Project Structure

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - `commonMain` is for code that's common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
      `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you're sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

## Core Utilities

The project includes several core utilities to help with common tasks:

### Network

The network module provides utilities for making network requests.

- `NetworkClient`: Interface for making network requests
- `SimpleNetworkClient`: Simple implementation of NetworkClient

### Image Loading

The image loading module provides utilities for loading images from network URLs.

- `NetworkImageLoader`: Interface for loading images from network URLs
- `SimpleNetworkImageLoader`: Simple implementation of NetworkImageLoader

### Local Storage

The local storage module provides utilities for storing key-value pairs.

- `KeyValueStorage`: Interface for key-value storage
- `InMemoryKeyValueStorage`: Simple in-memory implementation of KeyValueStorage

### Database

The database module provides utilities for storing and retrieving IPTV data.

- `IPTVDatabase`: Interface for the IPTV database
- `InMemoryIPTVDatabase`: Simple in-memory implementation of IPTVDatabase

### Firebase

The Firebase module provides utilities for integrating with Firebase services.

- `FirebaseAuth`: Interface for Firebase Authentication
- `InMemoryFirebaseAuth`: Simple in-memory implementation of FirebaseAuth
- `FirebaseFirestore`: Interface for Firebase Firestore
- `InMemoryFirebaseFirestore`: Simple in-memory implementation of FirebaseFirestore
- `FirebaseStorage`: Interface for Firebase Storage
- `InMemoryFirebaseStorage`: Simple in-memory implementation of FirebaseStorage

For detailed instructions on how to integrate Firebase with each platform, see the [Firebase Integration Guide](#firebase-integration-guide) below.

### Media Player

The media player module provides utilities for playing media.

- `MediaPlayer`: Interface for a media player
- `SimpleMediaPlayer`: Simple implementation of MediaPlayer

### IPTV Parser

The IPTV parser module provides utilities for parsing IPTV playlists.

- `IPTVParser`: Interface for parsing IPTV playlists
- `M3UParser`: Implementation for M3U format
- `XMLParser`: Implementation for XML format
- `JSONParser`: Implementation for JSON format
- `IPTVParserFactory`: Factory for creating IPTV parsers

For more details, see the [Core Utilities README](composeApp/src/commonMain/kotlin/tss/t/tsiptv/core/README.md).

## Getting Started

1. Clone the repository
2. Open the project in Android Studio or IntelliJ IDEA
3. Run the application on your desired platform (Android, iOS, or Desktop)

## Firebase Integration Guide

This guide provides instructions for integrating Firebase with each platform (Android, iOS, and Desktop) in this Kotlin Multiplatform project.

### Prerequisites

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Register your app for each platform (Android, iOS, Desktop)

### Android Integration

1. **Add the google-services.json file**:
   - Download the `google-services.json` file from the Firebase Console
   - Place it in the `composeApp` directory

2. **Configure the build.gradle.kts files**:
   - The project is already configured with the necessary plugins and dependencies:
     - Google Services plugin
     - Firebase Crashlytics plugin
     - Firebase BOM (Bill of Materials)
     - Firebase Analytics, Auth, Firestore, Storage, and Crashlytics dependencies

3. **Implement platform-specific Firebase classes**:
   - Create Android-specific implementations of the Firebase interfaces:
     - `AndroidFirebaseAuth` implementing `FirebaseAuth`
     - `AndroidFirebaseFirestore` implementing `FirebaseFirestore`
     - `AndroidFirebaseStorage` implementing `FirebaseStorage`

4. **Initialize Firebase in your Android app**:
   ```kotlin
   // In MainActivity.kt
   override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)

       // Initialize Firebase
       FirebaseApp.initializeApp(this)

       // Rest of your code...
   }
   ```

### iOS Integration

1. **Add the GoogleService-Info.plist file**:
   - Download the `GoogleService-Info.plist` file from the Firebase Console
   - Add it to your Xcode project by dragging it into the project navigator
   - Make sure to check "Copy items if needed" and add it to your app target

2. **Configure CocoaPods**:
   - Create or update the `Podfile` in the `iosApp` directory:
     ```ruby
     target 'iosApp' do
       use_frameworks!
       platform :ios, '14.0'

       # Firebase pods
       pod 'FirebaseCore'
       pod 'FirebaseAuth'
       pod 'FirebaseFirestore'
       pod 'FirebaseStorage'
       pod 'FirebaseAnalytics'
       pod 'FirebaseCrashlytics'
     end
     ```
   - Run `pod install` in the `iosApp` directory

3. **Initialize Firebase in your iOS app**:
   - Update `iOSApp.swift`:
     ```swift
     import SwiftUI
     import FirebaseCore

     @main
     struct iOSApp: App {
         init() {
             FirebaseApp.configure()
         }

         var body: some Scene {
             WindowGroup {
                 ContentView()
             }
         }
     }
     ```

4. **Implement platform-specific Firebase classes**:
   - Create iOS-specific implementations of the Firebase interfaces:
     - `IosFirebaseAuth` implementing `FirebaseAuth`
     - `IosFirebaseFirestore` implementing `FirebaseFirestore`
     - `IosFirebaseStorage` implementing `FirebaseStorage`

### Desktop Integration

Firebase doesn't have official support for desktop applications, but you can use the Firebase Admin SDK or Firebase REST APIs.

1. **Add Firebase Admin SDK dependencies**:
   - Update the `composeApp/build.gradle.kts` file to include Firebase Admin SDK:
     ```kotlin
     desktopMain.dependencies {
         // Existing dependencies...

         // Firebase Admin SDK
         implementation("com.google.firebase:firebase-admin:9.2.0")
     }
     ```

2. **Create a service account key**:
   - Go to Firebase Console > Project Settings > Service Accounts
   - Click "Generate new private key"
   - Save the JSON file securely

3. **Initialize Firebase Admin SDK**:
   ```kotlin
   // In your desktop-specific code
   fun initializeFirebase() {
       val serviceAccount = FileInputStream("path/to/serviceAccountKey.json")
       val options = FirebaseOptions.builder()
           .setCredentials(GoogleCredentials.fromStream(serviceAccount))
           .setDatabaseUrl("https://your-project-id.firebaseio.com")
           .build()

       FirebaseApp.initializeApp(options)
   }

   // Note: You'll need to import these classes:
   // com.google.auth.oauth2.GoogleCredentials
   // com.google.firebase.FirebaseApp
   // com.google.firebase.FirebaseOptions
   // java.io.FileInputStream
   ```

4. **Implement platform-specific Firebase classes**:
   - Create desktop-specific implementations of the Firebase interfaces:
     - `DesktopFirebaseAuth` implementing `FirebaseAuth`
     - `DesktopFirebaseFirestore` implementing `FirebaseFirestore`
     - `DesktopFirebaseStorage` implementing `FirebaseStorage`

### Common Implementation Notes

1. **Dependency Injection**:
   - Use a factory pattern or dependency injection to provide the correct platform-specific implementation:
     ```kotlin
     expect class FirebaseFactory {
         fun createAuth(): FirebaseAuth
         fun createFirestore(): FirebaseFirestore
         fun createStorage(): FirebaseStorage
     }

     // Android implementation
     actual class FirebaseFactory {
         actual fun createAuth(): FirebaseAuth = AndroidFirebaseAuth()
         actual fun createFirestore(): FirebaseFirestore = AndroidFirebaseFirestore()
         actual fun createStorage(): FirebaseStorage = AndroidFirebaseStorage()
     }

     // Similar implementations for iOS and Desktop
     ```

2. **Error Handling**:
   - Implement proper error handling for Firebase operations
   - Map platform-specific errors to the common `FirebaseAuthException`, `FirebaseFirestoreException`, and `FirebaseStorageException` classes

3. **Testing**:
   - Use the provided in-memory implementations for testing:
     - `InMemoryFirebaseAuth`
     - `InMemoryFirebaseFirestore`
     - `InMemoryFirebaseStorage`

## Learn More

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
Learn more about [Firebase](https://firebase.google.com/docs)…
