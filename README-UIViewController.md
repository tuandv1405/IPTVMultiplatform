# How to Call UIViewController from a Swift Framework Module

This guide explains how to create and call a UIViewController from a Swift framework module in a Kotlin Multiplatform Mobile (KMM) project.

## Table of Contents

1. [Overview](#overview)
2. [Creating a Swift Framework with UIViewController](#creating-a-swift-framework-with-uiviewcontroller)
3. [Exposing the UIViewController to Kotlin](#exposing-the-uiviewcontroller-to-kotlin)
4. [Calling the UIViewController from Kotlin](#calling-the-uiviewcontroller-from-kotlin)
5. [Example Implementation](#example-implementation)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)

## Overview

In a KMM project, you may need to present native iOS UI components that aren't available in Compose Multiplatform. This guide shows how to create a Swift framework containing UIViewController implementations and call them from your Kotlin code.

The interoperability between Swift and Kotlin works through Objective-C as an intermediary:

```
Swift <-> Objective-C <-> Kotlin/Native
```

## Creating a Swift Framework with UIViewController

1. **Create a new Swift framework project in Xcode**:
   - Open Xcode
   - File > New > Project
   - Select "Framework" under iOS
   - Name your framework (e.g., "MyUIKit")
   - Save it in a location within your project (e.g., `iosApp/Frameworks/MyUIKit`)

2. **Configure the framework settings**:
   - Select the framework target
   - Go to Build Settings
   - Set "Build Libraries for Distribution" to Yes
   - Set "Allow app extension API only" to No
   - Set "Defines Module" to Yes

3. **Create a UIViewController subclass**:
   ```swift
   import UIKit
   
   @objc public class MyViewController: UIViewController {
       
       @objc public init(title: String) {
           super.init(nibName: nil, bundle: nil)
           self.title = title
       }
       
       required init?(coder: NSCoder) {
           fatalError("init(coder:) has not been implemented")
       }
       
       public override func viewDidLoad() {
           super.viewDidLoad()
           view.backgroundColor = .white
           
           // Add your UI components here
           let label = UILabel()
           label.text = "Hello from Swift UIViewController!"
           label.translatesAutoresizingMaskIntoConstraints = false
           view.addSubview(label)
           
           NSLayoutConstraint.activate([
               label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
               label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
           ])
       }
   }
   ```

4. **Create a bridge class to present the UIViewController**:
   ```swift
   import UIKit
   
   @objc public class ViewControllerBridge: NSObject {
       
       @objc public static let shared = ViewControllerBridge()
       
       private override init() {
           super.init()
       }
       
       @objc public func presentViewController(title: String, completion: @escaping (Bool, Error?) -> Void) {
           // Get the top view controller to present our UI
           guard let topViewController = UIApplication.shared.windows.first?.rootViewController else {
               completion(false, NSError(domain: "ViewControllerBridge", code: -1, 
                   userInfo: [NSLocalizedDescriptionKey: "No view controller available to present UI"]))
               return
           }
           
           let viewController = MyViewController(title: title)
           topViewController.present(viewController, animated: true) {
               completion(true, nil)
           }
       }
       
       @objc public func dismissViewController(completion: @escaping (Bool, Error?) -> Void) {
           guard let topViewController = UIApplication.shared.windows.first?.rootViewController,
                 topViewController.presentedViewController != nil else {
               completion(false, NSError(domain: "ViewControllerBridge", code: -1, 
                   userInfo: [NSLocalizedDescriptionKey: "No presented view controller to dismiss"]))
               return
           }
           
           topViewController.dismiss(animated: true) {
               completion(true, nil)
           }
       }
   }
   ```

5. **Create a header file**:
   Create a file named "MyUIKit.h" with the following content:
   ```objc
   #import <Foundation/Foundation.h>
   
   //! Project version number for MyUIKit.
   FOUNDATION_EXPORT double MyUIKitVersionNumber;
   
   //! Project version string for MyUIKit.
   FOUNDATION_EXPORT const unsigned char MyUIKitVersionString[];
   
   // Export all public headers
   ```

6. **Build the framework**:
   - Select the appropriate destination (iOS device or simulator)
   - Build the framework (Product > Build)

## Exposing the UIViewController to Kotlin

1. **Create a def file**:
   Create a file at `src/nativeInterop/cinterop/MyUIKit.def` with the following content:
   ```
   language = Objective-C
   package = your.package.name.native
   modules = MyUIKit
   ```

2. **Configure Kotlin/Native interop in build.gradle.kts**:
   ```kotlin
   kotlin {
       // Existing configuration...
   
       iosX64 {
           compilations.getByName("main") {
               cinterops {
                   create("MyUIKit") {
                       defFile = project.file("src/nativeInterop/cinterop/MyUIKit.def")
                       includeDirs.allHeaders("iosApp/Frameworks/MyUIKit")
                       compilerOpts("-F${rootProject.projectDir}/iosApp/Frameworks/MyUIKit/build/Release-iphonesimulator")
                   }
               }
           }
       }
   
       // Similar configuration for iosArm64 and iosSimulatorArm64
   }
   ```

3. **Generate the bindings**:
   Run the Gradle task to generate the bindings:
   ```bash
   ./gradlew cinteropMyUIKitIosX64
   ```

## Calling the UIViewController from Kotlin

1. **Create a Kotlin wrapper class**:
   ```kotlin
   class MyUIController {
       private val bridge = ViewControllerBridge.shared
   
       suspend fun presentViewController(title: String): Boolean {
           return suspendCancellableCoroutine { continuation ->
               bridge.presentViewController(
                   title = title
               ) { success, error ->
                   if (success) {
                       continuation.resume(true)
                   } else {
                       val nsError = error as? NSError
                       continuation.resumeWithException(
                           Exception(nsError?.localizedDescription ?: "Failed to present view controller")
                       )
                   }
               }
           }
       }
   
       suspend fun dismissViewController(): Boolean {
           return suspendCancellableCoroutine { continuation ->
               bridge.dismissViewController { success, error ->
                   if (success) {
                       continuation.resume(true)
                   } else {
                       val nsError = error as? NSError
                       continuation.resumeWithException(
                           Exception(nsError?.localizedDescription ?: "Failed to dismiss view controller")
                       )
                   }
               }
           }
       }
   }
   ```

2. **Use the wrapper in your Compose UI**:
   ```kotlin
   @Composable
   fun MyScreen() {
       val scope = rememberCoroutineScope()
       val myUIController = remember { MyUIController() }
   
       Button(onClick = {
           scope.launch {
               try {
                   myUIController.presentViewController("My View Controller")
               } catch (e: Exception) {
                   println("Error presenting view controller: ${e.message}")
               }
           }
       }) {
           Text("Show Native UI")
       }
   
       Button(onClick = {
           scope.launch {
               try {
                   myUIController.dismissViewController()
               } catch (e: Exception) {
                   println("Error dismissing view controller: ${e.message}")
               }
           }
       }) {
           Text("Dismiss Native UI")
       }
   }
   ```

## Example Implementation

Here's a complete example of how to implement and call a UIViewController from a Swift framework:

### Swift Framework (MyUIKit)

```swift
// ViewControllerBridge.swift
import UIKit

@objc public class ViewControllerBridge: NSObject {
    
    @objc public static let shared = ViewControllerBridge()
    
    private override init() {
        super.init()
    }
    
    @objc public func presentViewController(title: String, completion: @escaping (Bool, Error?) -> Void) {
        guard let topViewController = UIApplication.shared.windows.first?.rootViewController else {
            completion(false, NSError(domain: "ViewControllerBridge", code: -1, 
                userInfo: [NSLocalizedDescriptionKey: "No view controller available to present UI"]))
            return
        }
        
        let viewController = MyViewController(title: title)
        topViewController.present(viewController, animated: true) {
            completion(true, nil)
        }
    }
    
    @objc public func dismissViewController(completion: @escaping (Bool, Error?) -> Void) {
        guard let topViewController = UIApplication.shared.windows.first?.rootViewController,
              topViewController.presentedViewController != nil else {
            completion(false, NSError(domain: "ViewControllerBridge", code: -1, 
                userInfo: [NSLocalizedDescriptionKey: "No presented view controller to dismiss"]))
            return
        }
        
        topViewController.dismiss(animated: true) {
            completion(true, nil)
        }
    }
}

// MyViewController.swift
import UIKit

@objc public class MyViewController: UIViewController {
    
    @objc public init(title: String) {
        super.init(nibName: nil, bundle: nil)
        self.title = title
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        
        // Add your UI components here
        let label = UILabel()
        label.text = "Hello from Swift UIViewController!"
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
}
```

### Kotlin Implementation

```kotlin
// MyUIController.kt
class MyUIController {
    private val bridge = ViewControllerBridge.shared

    suspend fun presentViewController(title: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bridge.presentViewController(
                title = title
            ) { success, error ->
                if (success) {
                    continuation.resume(true)
                } else {
                    val nsError = error as? NSError
                    continuation.resumeWithException(
                        Exception(nsError?.localizedDescription ?: "Failed to present view controller")
                    )
                }
            }
        }
    }

    suspend fun dismissViewController(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            bridge.dismissViewController { success, error ->
                if (success) {
                    continuation.resume(true)
                } else {
                    val nsError = error as? NSError
                    continuation.resumeWithException(
                        Exception(nsError?.localizedDescription ?: "Failed to dismiss view controller")
                    )
                }
            }
        }
    }
}
```

## Best Practices

1. **Use @objc annotations consistently**:
   - Mark all Swift classes, methods, and properties that need to be exposed to Kotlin with `@objc`
   - For classes that need to be subclassed from Kotlin, use `@objcMembers`

2. **Handle errors properly**:
   - Always provide error information in completion handlers
   - Convert Swift errors to Kotlin exceptions using suspendCancellableCoroutine

3. **Manage memory carefully**:
   - Be aware of retain cycles when using closures
   - Use weak references where appropriate

4. **Keep the Swift code simple**:
   - Avoid complex Swift features that don't translate well to Objective-C
   - Stick to basic types and patterns that work well with interop

5. **Use a bridge pattern**:
   - Create a dedicated bridge class to handle the presentation of view controllers
   - Use the singleton pattern for easy access from Kotlin

## Troubleshooting

1. **Class not found in Kotlin**:
   - Ensure the class is marked with `@objc` in Swift
   - Check that the module name in the def file matches the framework name

2. **Cannot present view controller**:
   - Make sure you're getting the correct root view controller
   - For iOS 15+, use `UIApplication.shared.connectedScenes.first as? UIWindowScene` to get the window scene

3. **Build errors**:
   - Ensure all framework dependencies are properly linked
   - Check that the framework search paths are correct in the cinterop configuration

4. **Runtime crashes**:
   - Check for nil values before using them
   - Ensure the view controller hierarchy is properly set up

5. **UI not appearing**:
   - Verify that the view controller is being presented on the main thread
   - Check that the view controller's view is properly configured