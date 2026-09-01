#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <mutex>
#include <string>

namespace {
constexpr const char* kLogTag = "LuaClientNative";
constexpr const char* kVersion = "0.1.1";
constexpr const char* kArchitecture = "arm64-v8a";

std::atomic_bool gInitialized{false};
std::mutex gStateMutex;
std::string gDataDirectory;

void logInfo(const char* message) {
    __android_log_write(ANDROID_LOG_INFO, kLogTag, message);
}

jstring toJString(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value == nullptr ? "" : value);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_lua_LuaNativeBridge_nativeInitialize(
        JNIEnv* env, jclass, jstring dataDirectory) {
    if (dataDirectory == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Diretorio de dados ausente");
        return JNI_FALSE;
    }

    const char* rawDirectory = env->GetStringUTFChars(dataDirectory, nullptr);
    if (rawDirectory == nullptr) {
        return JNI_FALSE;
    }

    {
        std::lock_guard<std::mutex> lock(gStateMutex);
        gDataDirectory.assign(rawDirectory);
    }
    env->ReleaseStringUTFChars(dataDirectory, rawDirectory);

    gInitialized.store(true, std::memory_order_release);
    logInfo("Lua Client Mobile native core v0.1.1 inicializado");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_lua_LuaNativeBridge_nativeIsInitialized(
        JNIEnv*, jclass) {
    return gInitialized.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_lua_LuaNativeBridge_nativeGetVersion(
        JNIEnv* env, jclass) {
    return toJString(env, kVersion);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_lua_LuaNativeBridge_nativeGetArchitecture(
        JNIEnv* env, jclass) {
    return toJString(env, kArchitecture);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_lua_LuaNativeBridge_nativeGetRendererSupport(
        JNIEnv* env, jclass) {
    return toJString(env, "OpenGL ES/Vulkan via Android overlay bridge");
}
