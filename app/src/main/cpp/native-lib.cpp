#include <jni.h>
#include <string>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <android/log.h>
#include <errno.h>
#include <fstream>
#include <sstream>

#define TAG "VioletNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_violet_safe_MainActivity_checkForSuBinaryNative(JNIEnv *env, jobject thiz) {
    const char *suPaths[] = {
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/sbin/su", "/su/bin/su", "/system/bin/su", "/system/bin/.ext/su",
            "/system/bin/failsafe/su", "/system/sd/xbin/su", "/system/usr/we-need-root/su",
            "/system/xbin/su", "/cache/su", "/data/su", "/dev/su"
    };

    // 方法1: access
    for (const char *path : suPaths) {
        if (access(path, F_OK) == 0) {
            LOGI("Found su via access: %s", path);
            return JNI_TRUE;
        }
    }

    // 方法2: stat
    struct stat st;
    for (const char *path : suPaths) {
        if (stat(path, &st) == 0) {
            LOGI("Found su via stat: %s", path);
            return JNI_TRUE;
        }
    }

    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_violet_safe_MainActivity_checkForSuspiciousMountsNative(JNIEnv *env, jobject thiz) {
    const char *mountPaths[] = {"/proc/mounts", "/proc/self/mounts"};
    const char *suspiciousKeywords[] = {
            "magisk", "core/mirror", "ksu", "apatch"
    };

    for (const char *path : mountPaths) {
        std::ifstream file(path);
        if (!file.is_open()) continue;

        std::string line;
        while (std::getline(file, line)) {
            for (const char *keyword : suspiciousKeywords) {
                if (line.find(keyword) != std::string::npos) {
                    LOGI("Suspicious mount found: %s", line.c_str());
                    return JNI_TRUE;
                }
            }
        }
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_violet_safe_MainActivity_checkForMagiskFilesNative(JNIEnv *env, jobject thiz) {
    const char *magiskPaths[] = {
            "/sbin/.magisk/", "/sbin/.core/mirror", "/sbin/.core/img",
            "/sbin/.core/db-0/magisk.db", "/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap"
    };

    for (const char *path : magiskPaths) {
        if (access(path, F_OK) == 0) {
            LOGI("Found magisk file via access: %s", path);
            return JNI_TRUE;
        }
    }

    // Direct syscall check (harder to hook than access)
    for (const char *path : magiskPaths) {
        long ret = syscall(__NR_faccessat, AT_FDCWD, path, F_OK, 0);
        if (ret == 0) {
            LOGI("Found magisk file via syscall faccessat: %s", path);
            return JNI_TRUE;
        }
    }

    return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_violet_safe_MainActivity_checkForZygiskNative(JNIEnv *env, jobject thiz) {
    std::ifstream file("/proc/self/maps");
    if (!file.is_open()) return JNI_FALSE;

    std::string line;
    while (std::getline(file, line)) {
        if (line.find("zygisk") != std::string::npos ||
            line.find("lspd") != std::string::npos ||
            line.find("riru") != std::string::npos ||
            line.find("shamiko") != std::string::npos) {
            LOGI("Zygisk/LSPosed/Riru trace found in maps: %s", line.c_str());
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}
