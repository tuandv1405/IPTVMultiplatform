# Step-by-Step Guide to Implementing FirebaseAuthKit Framework

This guide provides detailed instructions for implementing the FirebaseAuthKit framework for iOS Firebase Auth using Swift to use in Kotlin iosMain.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Step 1: Set Up the Xcode Project](#step-1-set-up-the-xcode-project)
4. [Step 2: Configure Firebase Dependencies](#step-2-configure-firebase-dependencies)
5. [Step 3: Implement the Swift Bridge](#step-3-implement-the-swift-bridge)
6. [Step 4: Configure Kotlin/Native Interop](#step-4-configure-kotlinnative-interop)
7. [Step 5: Integrate with Kotlin Code](#step-5-integrate-with-kotlin-code)
8. [Step 6: Test the Implementation](#step-6-test-the-implementation)
9. [Troubleshooting](#troubleshooting)
10. [References](#references)

## Prerequisites

- Xcode 15.0+
- Android Studio / IntelliJ IDEA
- Kotlin Multiplatform Mobile (KMM) project
- Firebase project set up in the Firebase console

## Project Structure

The final project structure will look like this:

```
TSIPTV/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── iosMain/
│       │   └── kotlin/
│       │       └── tss/t/tsiptv/core/firebase/
│       │           ├── IosFirebaseAuth.kt
│       │           └── IosFirebaseAuthImplementation.kt
│       └── nativeInterop/
│           └── cinterop/
│               └── FirebaseAuthKit.def
└── iosApp/
    └── Frameworks/
        └── FirebaseAuthKit/
            ├── FirebaseAuthKit/
            │   ├── FirebaseAuthKit.h
            │   └── FirebaseAuthBridge.swift
            └── FirebaseAuthKit.xcodeproj/
```

## Step 1: Set Up the Xcode Project

1. Open Xcode and create a new Framework project:
   - File > New > Project
   - Select "Framework" under iOS
   - Name it "FirebaseAuthKit"
   - Save it in the `iosApp/Frameworks` directory

2. Configure the framework settings:
   - Select the FirebaseAuthKit target
   - Go to Build Settings
   - Set "Build Libraries for Distribution" to Yes
   - Set "Allow app extension API only" to No
   - Set "Defines Module" to Yes
   - Set "Enable Modules (C and Objective-C)" to Yes

3. Create the header file:
   - Create a new file named "FirebaseAuthKit.h"
   - Add the following content:

```objc
#import <Foundation/Foundation.h>

//! Project version number for FirebaseAuthKit.
FOUNDATION_EXPORT double FirebaseAuthKitVersionNumber;

//! Project version string for FirebaseAuthKit.
FOUNDATION_EXPORT const unsigned char FirebaseAuthKitVersionString[];

// Export all public headers
```

## Step 2: Configure Firebase Dependencies

1. Add Firebase dependencies using Swift Package Manager:
   - In Xcode, go to File > Add Packages...
   - Search for "firebase-ios-sdk"
   - Select the Firebase iOS SDK package
   - Add the following products:
     - FirebaseAuth
     - FirebaseCore
     - GoogleUtilities (important for GoogleUtilities_NSData)

2. Ensure all dependencies are properly linked:
   - Go to the "General" tab of the FirebaseAuthKit target
   - Under "Frameworks, Libraries, and Embedded Content", ensure all Firebase dependencies are listed
   - If any are missing, click the "+" button and add them
   - Ensure "Embed & Sign" is selected for each dependency

## Step 3: Implement the Swift Bridge

1. Add the FirebaseAuthBridge.swift file:
   - Create a new Swift file named "FirebaseAuthBridge.swift"
   - Implement the bridge class with all necessary methods
   - Ensure all classes and methods are marked with `@objc` for Objective-C compatibility

2. The FirebaseAuthBridge.swift file should include:
   - A singleton instance
   - Methods for authentication operations (sign in, sign out, etc.)
   - Proper error handling
   - Conversion between Swift and Objective-C types

Example implementation:

```swift
import Foundation
import FirebaseAuth

// This class serves as a bridge between Kotlin and the Firebase iOS SDK
@objc class FirebaseAuthBridge: NSObject {

    // Singleton instance
    @objc static let shared = FirebaseAuthBridge()

    private override init() {
        super.init()
    }

    // Authentication methods
    @objc func signInWithEmail(email: String, password: String, completion: @escaping (String?, String?, String?, String?, Bool, Error?) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { (authResult, error) in
            // Implementation details...
        }
    }

    // Other methods...
}
```

## Step 4: Configure Kotlin/Native Interop

1. Create the def file:
   - Create a file at `composeApp/src/nativeInterop/cinterop/FirebaseAuthKit.def`
   - Add the following content:

```
language = Objective-C
package = tss.t.tsiptv.core.firebase.native
modules = FirebaseAuthKit
```

2. Update the build.gradle.kts file:
   - Ensure the cinterop configuration is properly set up
   - The configuration should include:
     - The def file path
     - The headers directory
     - The framework path

Example configuration:

```kotlin
kotlin {
    // Existing configuration...

    iosX64 {
        compilations.getByName("main") {
            cinterops {
                create("FirebaseAuthKit") {
                    defFile = project.file("src/nativeInterop/cinterop/FirebaseAuthKit.def")
                    includeDirs.allHeaders("iosApp/Frameworks/FirebaseAuthKit")
                    compilerOpts("-F${rootProject.projectDir}/iosApp/Frameworks/FirebaseAuthKit/build/Release-iphoneos")
                }
            }
        }
    }

    // Similar configuration for iosArm64 and iosSimulatorArm64
}
```

3. Build the framework:
   - In Xcode, select the appropriate destination (iOS device or simulator)
   - Build the framework (Product > Build)
   - Ensure the build succeeds without errors

## Step 5: Integrate with Kotlin Code

1. Create or update the IosFirebaseAuthImplementation.kt file:
   - Implement the FirebaseAuth interface
   - Use the generated bindings to call the Swift bridge methods
   - Handle conversion between Kotlin and Swift types

Example implementation:

```kotlin
class IosFirebaseAuthImplementation : FirebaseAuth {
    private val bridge = FirebaseAuthBridge.shared
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    override val currentUser: Flow<FirebaseUser?> = _currentUser

    // Implementation details...

    override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        return suspendCancellableCoroutine { continuation ->
            bridge.signInWithEmail(
                email = email,
                password = password
            ) { uid, email, displayName, photoUrl, isEmailVerified, error ->
                // Handle the result...
            }
        }
    }

    // Other methods...
}
```

2. Update the IosFirebaseInitializer.kt file:
   - Update the provideFirebaseAuth method to return the IosFirebaseAuthImplementation

```kotlin
fun provideFirebaseAuth(): FirebaseAuth {
    return IosFirebaseAuthImplementation()
}
```

## Step 6: Test the Implementation

1. Generate the Kotlin bindings:
   - Run the Gradle task to generate the bindings:
   ```bash
   ./gradlew cinteropFirebaseAuthKitIosX64
   ```

2. Build and run the iOS app:
   - Build the iOS app in Xcode or using Gradle
   - Run the app on a simulator or device
   - Test the authentication functionality

3. Verify the implementation:
   - Check that the app can sign in, sign out, etc.
   - Check that there are no errors related to missing modules

## Troubleshooting

### Missing GoogleUtilities_NSData Module

If you encounter the error "missing required module 'GoogleUtilities_NSData'":

1. Ensure GoogleUtilities is properly included in your framework's dependencies:
   - In Xcode, go to File > Add Packages...
   - Search for "firebase-ios-sdk"
   - Select the Firebase iOS SDK package
   - Add the GoogleUtilities product

2. Check the framework search paths:
   - In the Build Settings of your framework, check that the "Framework Search Paths" include the paths to all necessary frameworks

3. Rebuild the framework:
   - Clean the build (Product > Clean Build Folder)
   - Rebuild the framework (Product > Build)

### Other Common Issues

- **Undefined symbols**: Ensure all required frameworks are properly linked
- **Module not found**: Check the module name in the def file matches the framework name
- **Compilation errors**: Check the Swift code for errors and ensure it's compatible with Objective-C

## References

- [Kotlin/Native Objective-C Interop](https://kotlinlang.org/docs/native-objc-interop.html)
- [Firebase iOS SDK Documentation](https://firebase.google.com/docs/ios/setup)
- [Swift and Objective-C Interoperability](https://developer.apple.com/documentation/swift/swift_and_objective-c_interoperability)
