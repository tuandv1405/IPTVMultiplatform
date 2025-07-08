# FirebaseAuthKit Framework Setup Guide

## Introduction

This guide provides step-by-step instructions for setting up the FirebaseAuthKit framework with all the necessary Firebase dependencies, including GoogleUtilities_NSData.

## Issue

The error message `missing required module 'GoogleUtilities_NSData'` indicates that when building the FirebaseAuthKit framework, it's not properly including all the necessary Firebase dependencies.

## Solution

Follow these steps to properly set up the FirebaseAuthKit framework:

### 1. Create the Framework Project

1. Open Xcode
2. Create a new project (File > New > Project)
3. Select "Framework" under iOS
4. Name it "FirebaseAuthKit"
5. Save it in the `iosApp/Frameworks` directory (create this directory if it doesn't exist)

### 2. Add Firebase Dependencies

1. In the FirebaseAuthKit project, go to File > Add Packages...
2. Search for "firebase-ios-sdk"
3. Select the Firebase iOS SDK package
4. In the package options, select the following products:
   - FirebaseAuth
   - FirebaseCore
   - GoogleUtilities (this includes GoogleUtilities_NSData)
5. Click "Add Package"

### 3. Add FirebaseAuthBridge.swift

1. Copy the FirebaseAuthBridge.swift file from `iosApp/iosApp/FirebaseAuthBridge.swift` to the FirebaseAuthKit project
2. Ensure all classes and methods are marked with `@objc`

### 4. Create the Header File

1. Create a new header file named "FirebaseAuthKit.h"
2. Add the following content:

```objc
#import <Foundation/Foundation.h>

//! Project version number for FirebaseAuthKit.
FOUNDATION_EXPORT double FirebaseAuthKitVersionNumber;

//! Project version string for FirebaseAuthKit.
FOUNDATION_EXPORT const unsigned char FirebaseAuthKitVersionString[];

// Export all public headers
```

### 5. Configure the Framework

1. Select the FirebaseAuthKit target in Xcode
2. Go to Build Settings
3. Set "Build Libraries for Distribution" to Yes
4. Set "Allow app extension API only" to No
5. Set "Defines Module" to Yes
6. Set "Enable Modules (C and Objective-C)" to Yes
7. Set "Link Frameworks Automatically" to Yes

### 6. Configure Firebase Dependencies

1. Go to the "General" tab of the FirebaseAuthKit target
2. Under "Frameworks, Libraries, and Embedded Content", ensure all Firebase dependencies are listed
3. If any are missing, click the "+" button and add them
4. Ensure "Embed & Sign" is selected for each dependency

### 7. Build the Framework

1. Select the appropriate destination (iOS device or simulator)
2. Build the framework (Product > Build)
3. If there are any errors, check the error messages and fix them

### 8. Set Up Kotlin/Native Interop

Follow the instructions in the SwiftKotlinInteropGuide.md file to:

1. Add the cinterop configuration to your build.gradle.kts file
2. Create the def file at src/nativeInterop/cinterop/FirebaseAuthKit.def
3. Generate the Kotlin bindings

## Troubleshooting

If you still encounter the "missing required module 'GoogleUtilities_NSData'" error:

1. **Check Module Dependencies**: Ensure that GoogleUtilities is properly included in your framework's dependencies.
2. **Check Module Imports**: Make sure your Swift code is importing the necessary modules.
3. **Check Framework Search Paths**: In the Build Settings of your framework, check that the "Framework Search Paths" include the paths to all necessary frameworks.
4. **Check Module Map**: If you're using a module map, ensure it correctly maps all required modules.
5. **Use Explicit Imports**: In your Swift code, try explicitly importing GoogleUtilities if needed.

## Alternative Approach: Direct Integration

If creating a separate framework is causing too many issues, consider an alternative approach:

1. Add the FirebaseAuthBridge.swift file directly to your iOS app project
2. Ensure Firebase dependencies are properly added to the iOS app project
3. Use the Kotlin/Native interop to generate bindings directly for the iOS app's modules

This approach may be simpler and avoid some of the complexities of creating a separate framework.

## Conclusion

By following these steps, you should be able to properly set up the FirebaseAuthKit framework with all the necessary Firebase dependencies, including GoogleUtilities_NSData.