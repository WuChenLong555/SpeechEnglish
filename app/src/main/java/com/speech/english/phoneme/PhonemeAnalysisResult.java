package com.speech.english.phoneme;

/**
 * 音素分析结果
 */
public class PhonemeAnalysisResult {
    // 音素错误列表
    public PhonemeError[] errors;
    
    // 更新后的音素得分
    public float[] updatedScores;
    
    // 整体得分
    public float overallScore;
    
    public PhonemeAnalysisResult() {
        // 默认构造函数，供JNI使用
    }
}