#include "feature_manager.h"
#include <jni.h>
#include <string>
#include <android/log.h>

using namespace speech::features;

// 全局特征管理器实例
static FeatureManager* g_feature_manager = nullptr;

// 创建Java特征对象
jobject createFeaturesObject(JNIEnv* env, const FeatureManager::Features& features) {
    // 查找Features类
    jclass featuresClass = env->FindClass("com/example/speechenglish/FeatureExtractor$Features");
    if (featuresClass == nullptr) {
        FEATURE_LOGE("Failed to find Features class");
        return nullptr;
    }
    
    // 获取构造方法
    jmethodID constructor = env->GetMethodID(featuresClass, "<init>", "()V");
    if (constructor == nullptr) {
        FEATURE_LOGE("Failed to find Features constructor");
        return nullptr;
    }
    
    // 创建Features对象
    jobject featuresObj = env->NewObject(featuresClass, constructor);
    if (featuresObj == nullptr) {
        FEATURE_LOGE("Failed to create Features object");
        return nullptr;
    }
    
    // 设置字段值
    // 能量特征
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "energy", "F"), features.energy);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "energySlope", "F"), features.energy_slope);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "energyAcceleration", "F"), features.energy_acceleration);
    env->SetBooleanField(featuresObj, env->GetFieldID(featuresClass, "isSilence", "Z"), features.is_silence);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "silenceRatio", "F"), features.silence_ratio);
    
    // 基频特征
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "pitch", "F"), features.pitch);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "pitchSlope", "F"), features.pitch_slope);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "pitchAcceleration", "F"), features.pitch_acceleration);
    
    // 频谱特征
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "spectralFlux", "F"), features.spectral_flux);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "zcr", "F"), features.zcr);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "f1", "F"), features.f1);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "f2", "F"), features.f2);
    env->SetFloatField(featuresObj, env->GetFieldID(featuresClass, "formantFitness", "F"), features.formant_fitness);
    
    return featuresObj;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeInit(
    JNIEnv* env, jobject /* this */,
    jint sample_rate, jint frame_size, jint hop_length) {
    
    // 释放旧实例
    if (g_feature_manager) {
        delete g_feature_manager;
    }
    
    // 创建新实例
    g_feature_manager = new FeatureManager();
    return g_feature_manager->init(sample_rate, frame_size, hop_length);
}

JNIEXPORT jobject JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeProcessFrame(
    JNIEnv* env, jobject /* this */, jfloatArray frame) {
    
    if (!g_feature_manager) {
        FEATURE_LOGE("Feature manager not initialized");
        return nullptr;
    }
    
    // 获取帧数据
    jsize length = env->GetArrayLength(frame);
    jfloat* data = env->GetFloatArrayElements(frame, nullptr);
    
    // 处理帧
    bool success = g_feature_manager->processFrame(data, length);
    
    // 释放数据
    env->ReleaseFloatArrayElements(frame, data, JNI_ABORT);
    
    if (!success) {
        FEATURE_LOGE("Failed to process frame");
        return nullptr;
    }
    
    // 获取特征并创建Java对象
    auto features = g_feature_manager->getFeatures();
    return createFeaturesObject(env, features);
}

JNIEXPORT jboolean JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeIsConnectedSpeech(
    JNIEnv* env, jobject /* this */) {
    
    if (!g_feature_manager) {
        FEATURE_LOGE("Feature manager not initialized");
        return JNI_FALSE;
    }
    
    return g_feature_manager->isConnectedSpeech() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeSetThresholds(
    JNIEnv* env, jobject /* this */,
    jfloat energy_threshold,
    jfloat silence_ratio_threshold,
    jfloat energy_slope_threshold,
    jfloat energy_acceleration_threshold,
    jfloat pitch_slope_threshold,
    jfloat spectral_flux_threshold,
    jfloat formant_fitness_threshold) {
    
    if (!g_feature_manager) {
        FEATURE_LOGE("Feature manager not initialized");
        return;
    }
    
    g_feature_manager->setThresholds(
        energy_threshold,
        silence_ratio_threshold,
        energy_slope_threshold,
        energy_acceleration_threshold,
        pitch_slope_threshold,
        spectral_flux_threshold,
        formant_fitness_threshold
    );
}

JNIEXPORT void JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeReset(
    JNIEnv* env, jobject /* this */) {
    
    if (!g_feature_manager) {
        FEATURE_LOGE("Feature manager not initialized");
        return;
    }
    
    g_feature_manager->reset();
}

JNIEXPORT void JNICALL
Java_com_example_speechenglish_FeatureExtractor_nativeRelease(
    JNIEnv* env, jobject /* this */) {
    
    if (g_feature_manager) {
        delete g_feature_manager;
        g_feature_manager = nullptr;
    }
}

} // extern "C" 