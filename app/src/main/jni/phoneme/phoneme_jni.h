#pragma once

#include <jni.h>
#include "phoneme_analyzer.h"

namespace phoneme {

// JNI接口类
class PhonemeJNI {
public:
    // 初始化JNI接口
    static void Initialize(JNIEnv* env);
    
    // 注册本地方法
    static int RegisterNatives(JNIEnv* env);

    // JNI方法实现（公开以便静态命名的JNI包装函数调用）
    static jlong nativeCreate(JNIEnv* env, jobject thiz);
    static void nativeDestroy(JNIEnv* env, jobject thiz, jlong handle);
    
    // 分析音素并返回错误和优化后的得分
    static jobject nativeAnalyzePhonemes(
        JNIEnv* env, jobject thiz, jlong handle,
        jobjectArray wordSpans, jobjectArray words,
        jfloatArray logits, jint vocabSize
    );

private:
};

} // namespace phoneme