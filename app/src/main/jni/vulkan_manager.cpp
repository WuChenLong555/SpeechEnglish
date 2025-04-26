#include <jni.h>
#include <android/log.h>
#include <string>
#include <net.h>

#define TAG "VulkanManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct VulkanContext {
    ncnn::VulkanDevice* vkdev;
    bool hasGPU;

    VulkanContext() : vkdev(nullptr), hasGPU(false) {}
    ~VulkanContext() {
        if (vkdev) {
            delete vkdev;
            vkdev = nullptr;
        }
        ncnn::destroy_gpu_instance();
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_speechenglish_VulkanManager_nativeInit(JNIEnv* env, jobject thiz) {
    VulkanContext* context = new VulkanContext();

    // 初始化Vulkan
    if (ncnn::create_gpu_instance())
    {
        LOGE("Failed to create GPU instance!");
        return 0;
    }

    // 获取默认GPU设备
    context->vkdev = new ncnn::VulkanDevice();
    if (!context->vkdev->info.support_fp16_packed() || !context->vkdev->info.support_fp16_storage())
    {
        LOGE("Device does not support fp16!");
        delete context->vkdev;
        context->vkdev = nullptr;
        return 0;
    }

    context->hasGPU = true;
    LOGI("Vulkan initialized successfully");

    return (jlong)context;
}

JNIEXPORT jboolean JNICALL
Java_com_example_speechenglish_VulkanManager_nativeIsVulkanAvailable(JNIEnv* env, jobject thiz, jlong handle) {
    VulkanContext* context = (VulkanContext*)handle;
    return context && context->hasGPU;
}

JNIEXPORT void JNICALL
Java_com_example_speechenglish_VulkanManager_nativeRelease(JNIEnv* env, jobject thiz, jlong handle) {
    if (handle) {
        VulkanContext* context = (VulkanContext*)handle;
        delete context;
    }
}

}