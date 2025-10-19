#include "phoneme_jni.h"
#include "../force_aligner.h"
#include "../wav2vec2_model.h"
#include "../wav2vec2.h"
#include <string>
#include <vector>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "net.h"


#define LOG_TAG "PhonemeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace phoneme {

// 初始化JNI接口
void PhonemeJNI::Initialize(JNIEnv* env) {
    RegisterNatives(env);
}

// 注册本地方法
int PhonemeJNI::RegisterNatives(JNIEnv* env) {
    JNINativeMethod gMethods[] = {
        {"nativeCreate", "()J", (void*)PhonemeJNI::nativeCreate},
        {"nativeDestroy", "(J)V", (void*)PhonemeJNI::nativeDestroy},
        {"nativeAnalyzePhonemes", "(J[Ljava/lang/String;[I[FI[ILandroid/content/res/AssetManager;)Lcom/speech/english/phoneme/PhonemeAnalysisResult;", 
         (void*)PhonemeJNI::nativeAnalyzePhonemes}
    };

    jclass clazz = env->FindClass("com/speech/english/phoneme/PhonemeAnalyzer");
    if (clazz == nullptr) {
        LOGE("找不到PhonemeAnalyzer类");
        return JNI_ERR;
    }
    
    int result = env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
    env->DeleteLocalRef(clazz);
    
    return result;
}

// 创建音素分析器
jlong PhonemeJNI::nativeCreate(JNIEnv* env, jobject thiz) {
    PhonemeAnalyzer* analyzer = new PhonemeAnalyzer();
    return reinterpret_cast<jlong>(analyzer);
}

// 销毁音素分析器
void PhonemeJNI::nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    if (handle != 0) {
        PhonemeAnalyzer* analyzer = reinterpret_cast<PhonemeAnalyzer*>(handle);
        delete analyzer;
    }
}

// 分析音素并返回错误和优化后的得分
jobject PhonemeJNI::nativeAnalyzePhonemes(
    JNIEnv* env, jobject thiz, jlong handle,
    jobjectArray jWords, jintArray jTargets,
    jfloatArray jAudioData, jint blankId,
    jintArray jWordPhoneCounts, jobject jAssetManager) {
    
    PhonemeAnalyzer* analyzer = reinterpret_cast<PhonemeAnalyzer*>(handle);
    if (!analyzer) {
        LOGE("音素分析器为空");
        return nullptr;
    }
    
    // 转换words
    std::vector<std::string> words;
    jsize wordsLength = env->GetArrayLength(jWords);
    
    for (jsize i = 0; i < wordsLength; i++) {
        jstring jWord = (jstring)env->GetObjectArrayElement(jWords, i);
        const char* word = env->GetStringUTFChars(jWord, nullptr);
        words.push_back(word);
        env->ReleaseStringUTFChars(jWord, word);
        env->DeleteLocalRef(jWord);
    }
    
    // 转换targets
    std::vector<int> targets;
    jint* targetsArray = env->GetIntArrayElements(jTargets, nullptr);
    jsize targetsLength = env->GetArrayLength(jTargets);
    
    for (jsize i = 0; i < targetsLength; i++) {
        targets.push_back(targetsArray[i]);
    }
    env->ReleaseIntArrayElements(jTargets, targetsArray, JNI_ABORT);
    
    // 转换audioData
    std::vector<float> audioData;
    jfloat* audioArray = env->GetFloatArrayElements(jAudioData, nullptr);
    jsize audioLength = env->GetArrayLength(jAudioData);
    
    for (jsize i = 0; i < audioLength; i++) {
        audioData.push_back(audioArray[i]);
    }
    env->ReleaseFloatArrayElements(jAudioData, audioArray, JNI_ABORT);
    
    // 转换wordPhoneCounts
    std::vector<int> wordPhoneCounts;
    jint* wordPhoneCountsArray = env->GetIntArrayElements(jWordPhoneCounts, nullptr);
    jsize wordPhoneCountsLength = env->GetArrayLength(jWordPhoneCounts);
    
    for (jsize i = 0; i < wordPhoneCountsLength; i++) {
        wordPhoneCounts.push_back(wordPhoneCountsArray[i]);
    }
    env->ReleaseIntArrayElements(jWordPhoneCounts, wordPhoneCountsArray, JNI_ABORT);
    
    // 转换AssetManager
    AAssetManager* assetManager = AAssetManager_fromJava(env, jAssetManager);
    if (!assetManager) {
        LOGE("AssetManager转换失败");
        return nullptr;
    }
    
    // 初始化Wav2Vec2模型
    Wav2Vec2 wav2vec2Model;
    if (!wav2vec2Model.init(assetManager)) {
        LOGE("Wav2Vec2模型初始化失败");
        return nullptr;
    }
    
    // 使用Wav2Vec2进行推理
    bool inferenceSuccess = wav2vec2Model.processInference(audioData.data(), audioData.size());
    if (!inferenceSuccess) {
        LOGE("Wav2Vec2推理失败");
        return nullptr;
    }
    
    // 获取模型输出用于强制对齐和音素分析
    const ncnn::Mat& modelOutput = wav2vec2Model.getLastOutput();
    int vocabSize = wav2vec2::OutputConfig::NUM_TOKENS;
    int timeSteps = modelOutput.w;
    
    // blankId兜底处理
    int actualBlankId = blankId;
    if (actualBlankId < 0 || actualBlankId >= vocabSize) {
        actualBlankId = 0;
        LOGI("使用默认blankId: %d", actualBlankId);
    }
    
    // 直接使用lastOutput进行强制对齐
    speech::alignment::AlignmentResult alignResult = 
        speech::alignment::ForceAligner::align(modelOutput, targets, actualBlankId);
    
    if (alignResult.empty()) {
        LOGE("强制对齐失败");
        return nullptr;
    }
    
    // 根据wordPhoneCounts切分segments为wordSpans
    std::vector<std::vector<PhonemeSpan>> wordSpans;
    
    // 检查边界
    int totalPhonemes = 0;
    for (int count : wordPhoneCounts) {
        totalPhonemes += count;
    }
    
    if (totalPhonemes > (int)alignResult.segments.size()) {
        LOGE("wordPhoneCounts总和(%d)超过segments数量(%zu)", totalPhonemes, alignResult.segments.size());
        return nullptr;
    }
    
    // 按wordPhoneCounts切分segments
    int segmentIndex = 0;
    for (size_t wordIdx = 0; wordIdx < wordPhoneCounts.size(); wordIdx++) {
        std::vector<PhonemeSpan> spans;
        
        for (int phoneIdx = 0; phoneIdx < wordPhoneCounts[wordIdx]; phoneIdx++) {
            if (segmentIndex >= (int)alignResult.segments.size()) {
                LOGE("segmentIndex越界: %d >= %zu", segmentIndex, alignResult.segments.size());
                return nullptr;
            }
            
            const auto& segment = alignResult.segments[segmentIndex];
            
            PhonemeSpan span;
            span.token = segment.token;
            span.start = segment.startFrame;
            span.end = segment.endFrame;
            span.score = segment.scoreMean;
            
            // 填充span.text (需要tokenMapper，这里暂时留空)
            span.text = "";
            
            spans.push_back(span);
            segmentIndex++;
        }
        
        wordSpans.push_back(spans);
    }
    
    // 直接从modelOutput构造logits向量，避免重复复制
    int totalElements = vocabSize * timeSteps;
    std::vector<float> logits(totalElements);
    memcpy(logits.data(), modelOutput.data, totalElements * sizeof(float));
    
    // 分析音素
    PhonemeAnalysisResult result = analyzer->analyzePhonemes(wordSpans, words, logits, vocabSize);
    
    // 创建Java的PhonemeAnalysisResult对象
    jclass resultClass = env->FindClass("com/speech/english/phoneme/PhonemeAnalysisResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject jResult = env->NewObject(resultClass, constructor);
    
    // 设置errors
    jclass errorClass = env->FindClass("com/speech/english/phoneme/PhonemeError");
    jmethodID errorConstructor = env->GetMethodID(errorClass, "<init>", 
        "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;FFF)V");
    
    jobjectArray jErrors = env->NewObjectArray(result.errors.size(), errorClass, nullptr);
    
    for (size_t i = 0; i < result.errors.size(); i++) {
        const PhonemeError& error = result.errors[i];
        
        jstring jType = env->NewStringUTF(errorTypeToString(error.type).c_str());
        jstring jExpected = env->NewStringUTF(error.expectedPhoneme.c_str());
        jstring jActual = env->NewStringUTF(error.actualPhoneme.c_str());
        
        jobject jError = env->NewObject(errorClass, errorConstructor,
            jType, error.position, jExpected, jActual,
            error.confidence, error.timeStart, error.timeEnd);
        
        env->SetObjectArrayElement(jErrors, i, jError);
        
        env->DeleteLocalRef(jType);
        env->DeleteLocalRef(jExpected);
        env->DeleteLocalRef(jActual);
        env->DeleteLocalRef(jError);
    }
    
    // 设置updatedScores
    jfloatArray jScores = env->NewFloatArray(result.updatedScores.size());
    env->SetFloatArrayRegion(jScores, 0, result.updatedScores.size(), result.updatedScores.data());
    
    // 设置overallScore
    jfieldID errorsField = env->GetFieldID(resultClass, "errors", "[Lcom/speech/english/phoneme/PhonemeError;");
    jfieldID scoresField = env->GetFieldID(resultClass, "updatedScores", "[F");
    jfieldID overallField = env->GetFieldID(resultClass, "overallScore", "F");
    
    env->SetObjectField(jResult, errorsField, jErrors);
    env->SetObjectField(jResult, scoresField, jScores);
    env->SetFloatField(jResult, overallField, result.overallScore);
    
    // 清理
    env->DeleteLocalRef(resultClass);
    env->DeleteLocalRef(errorClass);
    env->DeleteLocalRef(jErrors);
    env->DeleteLocalRef(jScores);
    
    return jResult;
}

} // namespace phoneme

// 为静态命名解析提供JNI包装函数（同时保留RegisterNatives注册）
extern "C" JNIEXPORT jlong JNICALL Java_com_speech_english_phoneme_PhonemeAnalyzer_nativeCreate(JNIEnv* env, jobject thiz) {
    return phoneme::PhonemeJNI::nativeCreate(env, thiz);
}

extern "C" JNIEXPORT void JNICALL Java_com_speech_english_phoneme_PhonemeAnalyzer_nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    phoneme::PhonemeJNI::nativeDestroy(env, thiz, handle);
}

extern "C" JNIEXPORT jobject JNICALL Java_com_speech_english_phoneme_PhonemeAnalyzer_nativeAnalyzePhonemes(
    JNIEnv* env, jobject thiz, jlong handle, jobjectArray words, jintArray targets, 
    jfloatArray audioData, jint blankId, jintArray wordPhoneCounts, jobject jAssetManager) {
    return phoneme::PhonemeJNI::nativeAnalyzePhonemes(env, thiz, handle, words, targets, audioData, blankId, wordPhoneCounts, jAssetManager);
}

// JNI加载函数
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    phoneme::PhonemeJNI::Initialize(env);
    
    return JNI_VERSION_1_6;
}