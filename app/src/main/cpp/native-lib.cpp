#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/utsname.h>
#include <array>
#include <algorithm>
#include <mutex>
#include <string>

#define LOG_TAG "TroxzyNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex state_mutex;
bool frame_pacing_enabled = false;
bool adaptive_cache_enabled = false;
bool telemetry_enabled = true;
int profile_level = 50;

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

const char* architecture() {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#else
    return "unknown";
#endif
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_troxzy_panel_NativeBridge_nativeInitialize(JNIEnv* env, jclass) {
    struct utsname system_info {};
    const std::string kernel = uname(&system_info) == 0 ? system_info.release : "unavailable";
    const std::string message = std::string("Native diagnostics ready · ") + architecture() + " · kernel " + kernel;
    LOGI("Initialized safe, app-local diagnostics for PID %d (%s)", getpid(), architecture());
    return to_jstring(env, message);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_troxzy_panel_NativeBridge_nativeIsRootAvailable(JNIEnv*, jclass) {
    // Advisory only: discover whether a conventional su executable exists. No command is executed.
    constexpr std::array<const char*, 4> candidates = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"
    };
    for (const char* candidate : candidates) {
        if (access(candidate, X_OK) == 0) {
            LOGI("A superuser executable is present at %s (not invoked)", candidate);
            return JNI_TRUE;
        }
    }
    LOGI("No conventional superuser executable found");
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_troxzy_panel_NativeBridge_nativeSetFeature(JNIEnv* env, jclass, jstring feature, jboolean enabled) {
    const std::string key = from_jstring(env, feature);
    const bool value = enabled == JNI_TRUE;
    std::lock_guard<std::mutex> lock(state_mutex);
    bool* destination = nullptr;
    if (key == "frame_pacing") destination = &frame_pacing_enabled;
    else if (key == "adaptive_cache") destination = &adaptive_cache_enabled;
    else if (key == "telemetry") destination = &telemetry_enabled;

    if (destination == nullptr) {
        LOGW("Rejected unknown app-local setting: %s", key.c_str());
        return to_jstring(env, "Unknown setting; no change applied");
    }
    *destination = value;
    LOGI("App-local setting %s = %s", key.c_str(), value ? "on" : "off");
    return to_jstring(env, key + (value ? " enabled for this app" : " disabled for this app"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_troxzy_panel_NativeBridge_nativeSetProfileLevel(JNIEnv* env, jclass, jint level) {
    const int bounded = std::clamp(static_cast<int>(level), 0, 100);
    {
        std::lock_guard<std::mutex> lock(state_mutex);
        profile_level = bounded;
    }
    LOGI("App-local profile level set to %d", bounded);
    return to_jstring(env, "App profile intensity: " + std::to_string(bounded) + "%");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_troxzy_panel_NativeBridge_nativeGetRuntimeInfo(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(state_mutex);
    const std::string info = std::string("PID ") + std::to_string(getpid()) +
        " · " + architecture() + "\nFrame pacing: " + (frame_pacing_enabled ? "on" : "off") +
        " · Adaptive cache: " + (adaptive_cache_enabled ? "on" : "off") +
        " · Telemetry: " + (telemetry_enabled ? "on" : "off") +
        " · Profile: " + std::to_string(profile_level) + "%";
    return to_jstring(env, info);
}
