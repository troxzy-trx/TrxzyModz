# TR∅XZY

TR∅XZY is an Android Studio application with a user-started floating control panel and a small C++17/JNI diagnostics library. The profile controls are intentionally scoped to the TR∅XZY process: they do not attach to, inspect, patch, or change other applications or the operating system.

## Build

Open this directory in Android Studio with Android SDK 35, Android NDK, CMake 3.22.1, and a Java 17 JDK installed. Sync Gradle and build the `app` configuration. The native library is built for `armeabi-v7a` and `arm64-v8a`.

## Use

Launch TR∅XZY, grant its overlay permission from the system settings screen, then tap **Launch overlay menu**. Drag the floating badge to reposition it. The service displays an ongoing notification while the overlay is active; close the panel using its `×` control.

The superuser check only reports whether a conventional `su` executable is present. It never invokes a shell or requests elevated execution. JNI controls only maintain app-local profile settings and diagnostics.
