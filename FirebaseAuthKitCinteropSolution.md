# FirebaseAuthKit Cinterop Solution

## Issue

The error message:
```
:composeApp:iosX64Main: cinterop file: /Users/tun/Tun/ComposeMultiPlatform/TSIPTV/composeApp/build/libs/composeApp-iosX64Cinterop-FirebaseAuthKitMain.klib does not exist
```

This error occurred because the Kotlin/Native cinterop tool couldn't generate the .klib file for the FirebaseAuthKit framework. The issue was caused by several factors:

1. The cinterop configuration in build.gradle.kts was pointing to a non-existent build directory
2. The FirebaseAuthKit.def file was using modules instead of headers
3. Cinterop commonization was disabled in the project

## Solution

The following changes were made to fix the issue:

### 1. Update the build.gradle.kts file

The cinterop configuration was updated to point to the correct location of the header files and to add configurations for all iOS targets:

```kotlin
iosX64 {
    compilations.getByName("main") {
        cinterops {
            create("FirebaseAuthKit") {
                defFile = project.file("src/nativeInterop/cinterop/FirebaseAuthKit.def")
                includeDirs.allHeaders("${rootProject.projectDir}/iosApp/Frameworks/FirebaseAuthKit/FirebaseAuthKit")
            }
        }
    }
}

iosArm64 {
    compilations.getByName("main") {
        cinterops {
            create("FirebaseAuthKit") {
                defFile = project.file("src/nativeInterop/cinterop/FirebaseAuthKit.def")
                includeDirs.allHeaders("${rootProject.projectDir}/iosApp/Frameworks/FirebaseAuthKit/FirebaseAuthKit")
            }
        }
    }
}

iosSimulatorArm64 {
    compilations.getByName("main") {
        cinterops {
            create("FirebaseAuthKit") {
                defFile = project.file("src/nativeInterop/cinterop/FirebaseAuthKit.def")
                includeDirs.allHeaders("${rootProject.projectDir}/iosApp/Frameworks/FirebaseAuthKit/FirebaseAuthKit")
            }
        }
    }
}
```

### 2. Update the FirebaseAuthKit.def file

The FirebaseAuthKit.def file was updated to use headers instead of modules:

```
language = Objective-C
package = tss.t.tsiptv.core.firebase.native
headers = FirebaseAuthKit.h
```

### 3. Enable cinterop commonization

Cinterop commonization was enabled in the gradle.properties file:

```
kotlin.mpp.enableCInteropCommonization=true
```

## Verification

After making these changes, the following tasks were run to verify the solution:

1. `./gradlew :composeApp:cinteropFirebaseAuthKitIosX64` - This task completed successfully, indicating that the .klib file was generated correctly.
2. `./gradlew :composeApp:compileKotlinIosX64` - This task completed successfully, indicating that the project can be built with the generated .klib file.

## Notes

- The FirebaseAuthKit framework doesn't need to be built before running the cinterop task, as the task now uses the header files directly.
- If you need to use the actual framework implementation, you'll need to build the framework using Xcode and update the cinterop configuration to point to the built framework.
- The warning about cinterop commonization being disabled has been resolved by enabling it in the gradle.properties file.