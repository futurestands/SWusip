#include <string.h>
#include <pthread.h>
#include <jni.h>
#include <android/log.h>

#define TAG "BaresipJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM *g_jvm = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    LOGI("JNI_OnLoad called successfully");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_BaresipService_baresipStart(
        JNIEnv *env, jobject instance, jstring jPath, jstring jAddrs, jint jLogLevel,
        jstring jSoftware) {
    (void)env; (void)instance; (void)jPath; (void)jAddrs; (void)jLogLevel; (void)jSoftware;
    LOGI("baresipStart stub called");
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_Api_log_1level_1set(
        JNIEnv *env, jobject instance, jint level) {
    (void)env; (void)instance; (void)level;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_Api_config_1verify_1server_1set(
        JNIEnv *env, jobject instance, jboolean verify) {
    (void)env; (void)instance; (void)verify;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_Api_net_1use_1nameserver(
        JNIEnv *env, jobject instance, jstring servers) {
    (void)env; (void)instance; (void)servers;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_Api_module_1load(
        JNIEnv *env, jobject instance, jstring module) {
    (void)env; (void)instance; (void)module;
    return 0;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_Api_module_1unload(
        JNIEnv *env, jobject instance, jstring module) {
    (void)env; (void)instance; (void)module;
}

JNIEXPORT jlong JNICALL Java_com_tutpro_baresip_Api_ua_1alloc(
        JNIEnv *env, jobject instance, jstring uri) {
    (void)env; (void)instance; (void)uri;
    return 12345L;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_Api_ua_1destroy(
        JNIEnv *env, jobject instance, jlong uap) {
    (void)env; (void)instance; (void)uap;
}

JNIEXPORT jint JNICALL Java_com_tutpro_baresip_Api_ua_1register(
        JNIEnv *env, jobject instance, jlong uap) {
    (void)env; (void)instance; (void)uap;
    return 0;
}

JNIEXPORT void JNICALL Java_com_tutpro_baresip_Api_ua_1unregister(
        JNIEnv *env, jobject instance, jlong uap) {
    (void)env; (void)instance; (void)uap;
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_Api_account_1aor(
        JNIEnv *env, jobject instance, jlong acc) {
    (void)env; (void)instance; (void)acc;
    return (*env)->NewStringUTF(env, "sip:swusip@pbx.local");
}

JNIEXPORT jstring JNICALL Java_com_tutpro_baresip_Api_account_1display_1name(
        JNIEnv *env, jobject instance, jlong acc) {
    (void)env; (void)instance; (void)acc;
    return (*env)->NewStringUTF(env, "SWUSIP Call Centre");
}
