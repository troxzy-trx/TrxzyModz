package com.troxzy.panel

/** JNI surface for app-local runtime tuning and device diagnostics only. */
object NativeBridge {
    val isLoaded: Boolean

    init {
        isLoaded = try {
            System.loadLibrary("troxzy")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    @JvmStatic external fun nativeInitialize(): String
    @JvmStatic external fun nativeIsRootAvailable(): Boolean
    @JvmStatic external fun nativeSetFeature(feature: String, enabled: Boolean): String
    @JvmStatic external fun nativeSetProfileLevel(level: Int): String
    @JvmStatic external fun nativeGetRuntimeInfo(): String

    fun initialize(): String = if (isLoaded) nativeInitialize() else "Native diagnostics unavailable"
    fun rootAvailable(): Boolean = isLoaded && nativeIsRootAvailable()
    fun setFeature(feature: String, enabled: Boolean): String =
        if (isLoaded) nativeSetFeature(feature, enabled) else "Native library unavailable"
    fun setProfileLevel(level: Int): String =
        if (isLoaded) nativeSetProfileLevel(level) else "Native library unavailable"
    fun runtimeInfo(): String =
        if (isLoaded) nativeGetRuntimeInfo() else "Native runtime details unavailable"
}
