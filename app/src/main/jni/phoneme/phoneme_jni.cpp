#include "phoneme_jni.h"
#include <string>
#include <vector>
#include <android/log.h>

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
        {"nativeAnalyzePhonemes", "(J[[Ljava/lang/Object;[Ljava/lang/String;[FI)Lcom/speech/english/phoneme/PhonemeAnalysisResult;", 
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

// 将Java的PhonemeSpan转换为C++的PhonemeSpan
static PhonemeSpan convertPhonemeSpan(JNIEnv* env, jobject jSpan) {
    jclass spanClass = env->GetObjectClass(jSpan);
    
    jfieldID tokenField = env->GetFieldID(spanClass, "token", "I");
    jfieldID startField = env->GetFieldID(spanClass, "start", "I");
    jfieldID endField = env->GetFieldID(spanClass, "end", "I");
    jfieldID scoreField = env->GetFieldID(spanClass, "score", "F");
    jfieldID textField = env->GetFieldID(spanClass, "text", "Ljava/lang/String;");
    
    PhonemeSpan span;
    span.token = env->GetIntField(jSpan, tokenField);
    span.start = env->GetIntField(jSpan, startField);
    span.end = env->GetIntField(jSpan, endField);
    span.score = env->GetFloatField(jSpan, scoreField);
    
    jstring jText = (jstring)env->GetObjectField(jSpan, textField);
    const char* text = env->GetStringUTFChars(jText, nullptr);
    span.text = text;
    env->ReleaseStringUTFChars(jText, text);
    
    env->DeleteLocalRef(spanClass);
    env->DeleteLocalRef(jText);
    
    return span;
}

// 分析音素并返回错误和优化后的得分
jobject PhonemeJNI::nativeAnalyzePhonemes(
    JNIEnv* env, jobject thiz, jlong handle,
    jobjectArray jWordSpans, jobjectArray jWords,
    jfloatArray jLogits, jint vocabSize) {
    
    PhonemeAnalyzer* analyzer = reinterpret_cast<PhonemeAnalyzer*>(handle);
    if (!analyzer) {
        LOGE("音素分析器为空");
        return nullptr;
    }
    
    // 转换wordSpans
    std::vector<std::vector<PhonemeSpan>> wordSpans;
    jsize wordSpansLength = env->GetArrayLength(jWordSpans);
    
    for (jsize i = 0; i < wordSpansLength; i++) {
        jobjectArray jWordSpan = (jobjectArray)env->GetObjectArrayElement(jWordSpans, i);
        jsize spanLength = env->GetArrayLength(jWordSpan);
        
        std::vector<PhonemeSpan> spans;
        for (jsize j = 0; j < spanLength; j++) {
            jobject jSpan = env->GetObjectArrayElement(jWordSpan, j);
            spans.push_back(convertPhonemeSpan(env, jSpan));
            env->DeleteLocalRef(jSpan);
        }
        
        wordSpans.push_back(spans);
        env->DeleteLocalRef(jWordSpan);
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
    
    // 转换logits
    std::vector<float> logits;
    jfloat* logitsArray = env->GetFloatArrayElements(jLogits, nullptr);
    jsize logitsLength = env->GetArrayLength(jLogits);
    
    for (jsize i = 0; i < logitsLength; i++) {
        logits.push_back(logitsArray[i]);
    }
    
    env->ReleaseFloatArrayElements(jLogits, logitsArray, JNI_ABORT);
    
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
    JNIEnv* env, jobject thiz, jlong handle, jobjectArray wordSpans, jobjectArray words, jfloatArray logits, jint vocabSize) {
    return phoneme::PhonemeJNI::nativeAnalyzePhonemes(env, thiz, handle, wordSpans, words, logits, vocabSize);
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