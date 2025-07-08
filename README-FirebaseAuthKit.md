# FirebaseAuthKit Implementation

This repository contains the implementation of the FirebaseAuthKit framework for iOS Firebase Auth using Swift to use in Kotlin iosMain. This README provides an overview of the implementation and links to the relevant documentation.

## Overview

The FirebaseAuthKit framework is a Swift framework that provides a bridge between Kotlin and the Firebase iOS SDK for authentication operations. It allows the Kotlin code in the iosMain source set to interact with the Firebase Auth SDK on iOS.

## Files

- **FirebaseAuthKitImplementationGuide.md**: A step-by-step guide for implementing the FirebaseAuthKit framework.
- **TestFirebaseAuthKitImplementation.sh**: A script to test the FirebaseAuthKit implementation.

## Implementation Steps

The implementation of the FirebaseAuthKit framework involves the following steps:

1. **Set up the Xcode project**: Create a new Framework project in Xcode and configure it with the necessary settings.
2. **Configure Firebase dependencies**: Add the Firebase iOS SDK as a dependency using Swift Package Manager and ensure all required modules are included.
3. **Implement the Swift bridge**: Create a Swift class that serves as a bridge between Kotlin and the Firebase iOS SDK.
4. **Configure Kotlin/Native interop**: Set up the necessary configuration for Kotlin/Native to interact with the Swift code.
5. **Integrate with Kotlin code**: Update the Kotlin code to use the Swift bridge through the generated bindings.
6. **Test the implementation**: Build the framework, generate the Kotlin bindings, and test the authentication functionality.

For detailed instructions on each step, refer to the [FirebaseAuthKitImplementationGuide.md](FirebaseAuthKitImplementationGuide.md).

## Testing


This script checks if all the necessary files and configurations are in place, and provides guidance on how to proceed with testing the implementation.

## Troubleshooting

If you encounter any issues during the implementation or testing, refer to the Troubleshooting section in the [FirebaseAuthKitImplementationGuide.md](FirebaseAuthKitImplementationGuide.md).

## References

- [Kotlin/Native Objective-C Interop](https://kotlinlang.org/docs/native-objc-interop.html)
- [Firebase iOS SDK Documentation](https://firebase.google.com/docs/ios/setup)
- [Swift and Objective-C Interoperability](https://developer.apple.com/documentation/swift/swift_and_objective-c_interoperability)