# Swift-Kotlin Interop Guide for KMM Projects

This guide explains how to set up interoperability between Swift and Kotlin in a Kotlin Multiplatform Mobile (KMM) project, with a specific focus on exposing the `FirebaseAuthBridge.swift` file to Kotlin code.

## Table of Contents

1. [Understanding Swift-Kotlin Interop](#understanding-swift-kotlin-interop)
2. [Approaches to Swift-Kotlin Interop](#approaches-to-swift-kotlin-interop)
3. [Step-by-Step Guide](#step-by-step-guide)
   - [Creating a Swift Framework](#creating-a-swift-framework)
   - [Generating Kotlin Bindings](#generating-kotlin-bindings)
   - [Using the Generated Bindings](#using-the-generated-bindings)
4. [Example: Exposing FirebaseAuthBridge](#example-exposing-firebaseauthbridge)
5. [Best Practices and Common Pitfalls](#best-practices-and-common-pitfalls)

## Understanding Swift-Kotlin Interop

Kotlin/Native can interoperate with Objective-C and Swift code. The interop works through Objective-C as an intermediary:

```
Swift <-> Objective-C <-> Kotlin/Native
```

This means that Swift code needs to be exposed to Objective-C before it can be used from Kotlin. This is done by:

1. Marking Swift classes and methods with `@objc`
2. Creating a framework that exposes these classes and methods
3. Generating Kotlin bindings for this framework

## Approaches to Swift-Kotlin Interop

There are several approaches to Swift-Kotlin interop in KMM projects:

1. **Cocoapods Dependency Manager**: Using the Kotlin Cocoapods Gradle plugin
2. **Manual Framework Creation**: Creating a Swift framework manually and generating bindings
3. **Kotlin/Native cinterop Tool**: Using the cinterop tool directly

For this guide, we'll focus on the manual framework creation approach, as it gives the most control and is the most straightforward for our specific use case.

## Step-by-Step Guide

### Creating a Swift Framework

1. **Create a new Swift framework project in Xcode**:
   - Open Xcode
   - File > New > Project
   - Select "Framework" under iOS, watchOS, or tvOS
   - Name it "FirebaseAuthKit" (or any name you prefer)
   - Save it in a location within your project (e.g., `iosApp/Frameworks/FirebaseAuthKit`)

2. **Add your Swift files to the framework**:
   - Copy `FirebaseAuthBridge.swift` to the framework project
   - Make sure all classes and methods are marked with `@objc`
   - Create a header file to expose the classes to Objective-C

3. **Configure the framework**:
   - Set "Build Libraries for Distribution" to Yes
   - Set "Allow app extension API only" to No
   - Set "Defines Module" to Yes

4. **Build the framework**:
   - Select the appropriate target (iOS device or simulator)
   - Build the framework (Product > Build)

### Generating Kotlin Bindings

1. **Add the cinterop configuration to your Gradle build**:

   Add the following to your `build.gradle.kts` file:

   ```kotlin
   kotlin {
       // Existing configuration...

       iosX64 {
           compilations.getByName("main") {
               cinterops {
                   create("FirebaseAuthKit") {
                       defFile = project.file("src/nativeInterop/cinterop/FirebaseAuthKit.def")
                       includeDirs.allHeaders("iosApp/Frameworks/FirebaseAuthKit")
                       compilerOpts("-F${rootProject.projectDir}/iosApp/Frameworks/FirebaseAuthKit/build/Release-iphonesimulator")
                   }
               }
           }
       }

       // Similar configuration for iosArm64 and iosSimulatorArm64
   }
   ```

2. **Create the def file**:

   Create a file at `src/nativeInterop/cinterop/FirebaseAuthKit.def` with the following content:

   ```
   language = Objective-C
   package = tss.t.tsiptv.core.firebase.native
   modules = FirebaseAuthKit
   ```

3. **Generate the bindings**:

   Run the Gradle task to generate the bindings:

   ```bash
   ./gradlew cinteropFirebaseAuthKitIosX64
   ```

### Using the Generated Bindings

1. **Import the generated bindings in your Kotlin code**:

   ```kotlin
   // Import the generated bindings
   val FirebaseAuthBridge = FirebaseAuthKit.FirebaseAuthBridge
   ```

2. **Use the Swift classes and methods**:

   ```kotlin
   val bridge = FirebaseAuthBridge.shared
   ```

3. **Convert between Swift and Kotlin types**:

   You'll need to handle type conversions between Swift and Kotlin. For example:

   ```kotlin
   // Convert Swift callback to Kotlin suspend function
   suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
       return suspendCancellableCoroutine { continuation ->
           bridge.signInWithEmail(
               email = email,
               password = password
           ) { uid, email, displayName, photoUrl, isEmailVerified, error ->
               if (error != null) {
                   val nsError = error as NSError
                   continuation.resumeWithException(
                       FirebaseAuthException(
                           nsError.domain,
                           nsError.localizedDescription ?: "Unknown error"
                       )
                   )
               } else if (uid != null) {
                   continuation.resume(
                       FirebaseUser(
                           uid = uid,
                           email = email,
                           displayName = displayName,
                           photoUrl = photoUrl,
                           isEmailVerified = isEmailVerified
                       )
                   )
               } else {
                   continuation.resumeWithException(
                       FirebaseAuthException("auth/unknown", "Unknown error signing in")
                   )
               }
           }
       }
   }
   ```

## Example: Exposing FirebaseAuthBridge

Let's apply the steps above to expose the `FirebaseAuthBridge.swift` file to Kotlin:

1. **Ensure FirebaseAuthBridge.swift is properly annotated**:

   The file is already properly annotated with `@objc` for all classes and methods.

2. **Create a Swift framework project**:

   Create a new framework project named "FirebaseAuthKit" and add the `FirebaseAuthBridge.swift` file to it.

3. **Create a header file**:

   Create a file named `FirebaseAuthKit.h` with the following content:

   ```objc
   #import <Foundation/Foundation.h>

   //! Project version number for FirebaseAuthKit.
   FOUNDATION_EXPORT double FirebaseAuthKitVersionNumber;

   //! Project version string for FirebaseAuthKit.
   FOUNDATION_EXPORT const unsigned char FirebaseAuthKitVersionString[];

   // Export all public headers
   ```

4. **Configure the framework**:

   Configure the framework as described in the "Creating a Swift Framework" section.

5. **Add Firebase dependencies**:

   Add the Firebase dependencies to the framework using Swift Package Manager:
   - Open the framework project in Xcode
   - File > Add Packages...
   - Search for "firebase-ios-sdk"
   - Add the FirebaseAuth product

6. **Build the framework**:

   Build the framework for both device and simulator architectures.

7. **Add cinterop configuration**:

   Add the cinterop configuration to your `build.gradle.kts` file as described in the "Generating Kotlin Bindings" section.

8. **Create the def file**:

   Create the `FirebaseAuthKit.def` file as described.

9. **Generate the bindings**:

   Run the Gradle task to generate the bindings.

10. **Update IosFirebaseAuth.kt**:

    Update the `IosFirebaseAuth.kt` file to use the generated bindings:

    ```kotlin
    // This would be in your IosFirebaseAuth.kt file
    // Imports and package declaration omitted for brevity

    class IosFirebaseAuth : FirebaseAuth {
        private val bridge = FirebaseAuthBridge.shared
        private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
        override val currentUser: Flow<FirebaseUser?> = _currentUser

        init {
            // Set up auth state listener
            val listener = bridge.addAuthStateListener { userData ->
                if (userData != null) {
                    val uid = userData["uid"] as String
                    val email = userData["email"] as String
                    val displayName = userData["displayName"] as String
                    val photoUrl = userData["photoUrl"] as String
                    val isEmailVerified = userData["isEmailVerified"] as Boolean

                    _currentUser.value = FirebaseUser(
                        uid = uid,
                        email = email,
                        displayName = displayName,
                        photoUrl = photoUrl,
                        isEmailVerified = isEmailVerified
                    )
                } else {
                    _currentUser.value = null
                }
            }

            // Store the listener to remove it later if needed
            // This is just an example, you might want to handle this differently
            this.authStateListener = listener
        }

        private var authStateListener: Any? = null

        override suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
            return suspendCancellableCoroutine { continuation ->
                bridge.signInWithEmail(
                    email = email,
                    password = password
                ) { uid, email, displayName, photoUrl, isEmailVerified, error ->
                    if (error != null) {
                        val nsError = error as NSError
                        continuation.resumeWithException(
                            FirebaseAuthException(
                                nsError.domain,
                                nsError.localizedDescription ?: "Unknown error"
                            )
                        )
                    } else if (uid != null) {
                        val user = FirebaseUser(
                            uid = uid,
                            email = email,
                            displayName = displayName,
                            photoUrl = photoUrl,
                            isEmailVerified = isEmailVerified
                        )
                        continuation.resume(user)
                    } else {
                        continuation.resumeWithException(
                            FirebaseAuthException("auth/unknown", "Unknown error signing in")
                        )
                    }
                }
            }
        }

        // Implement other methods similarly...
    }
    ```

## Best Practices and Common Pitfalls

### Best Practices

1. **Use @objc annotations consistently**:
   - Mark all Swift classes, methods, and properties that need to be exposed to Kotlin with `@objc`
   - For classes that need to be subclassed from Kotlin, use `@objcMembers`

2. **Handle type conversions carefully**:
   - Swift and Kotlin have different type systems
   - Use appropriate conversions between Swift and Kotlin types
   - Be especially careful with nullable types

3. **Use suspendCancellableCoroutine for callbacks**:
   - Convert Swift callbacks to Kotlin suspend functions using `suspendCancellableCoroutine`
   - This makes the code more idiomatic in Kotlin

4. **Keep the Swift code simple**:
   - Avoid complex Swift features that don't translate well to Objective-C
   - Stick to basic types and patterns that work well with interop

5. **Test thoroughly**:
   - Test the interop code thoroughly on both simulator and device
   - Different architectures can sometimes behave differently

### Common Pitfalls

1. **Missing @objc annotations**:
   - Forgetting to mark Swift classes or methods with `@objc` will make them invisible to Kotlin

2. **Unsupported Swift features**:
   - Some Swift features don't work with Objective-C interop, such as:
     - Generics
     - Tuples
     - Enums with associated values
     - Extensions on generic types

3. **Memory management issues**:
   - Be careful with object lifecycles and memory management
   - Use weak references where appropriate to avoid retain cycles

4. **Framework configuration issues**:
   - Make sure the framework is properly configured for distribution
   - Ensure the module name matches between the framework and the def file

5. **Architecture compatibility**:
   - Make sure the framework is built for all required architectures (arm64, x86_64)
   - Use universal (fat) frameworks or XCFrameworks for better compatibility

By following this guide, you should be able to successfully expose your Swift code to Kotlin in your KMM project, enabling seamless interoperability between the two languages.
