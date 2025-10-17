package com.speech.english.phoneme;

/**
 * 音素分析器 - Java接口
 * 通过JNI调用C++实现的音素分析和评分优化算法
 */
public class PhonemeAnalyzer {
    static {
        System.loadLibrary("phoneme_analyzer");
    }
    
    // 本地对象句柄
    private long nativeHandle;
    
    /**
     * 构造函数
     */
    public PhonemeAnalyzer() {
        nativeHandle = nativeCreate();
    }
    
    /**
     * 析构函数
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (nativeHandle != 0) {
                nativeDestroy(nativeHandle);
                nativeHandle = 0;
            }
        } finally {
            super.finalize();
        }
    }
    
    /**
     * 分析音素并返回错误和优化后的得分
     * 
     * @param wordSpans 单词级别的音素对齐结果
     * @param words 原始文本单词
     * @param logits 模型输出的logits
     * @param vocabSize 词汇表大小
     * @return 音素分析结果
     */
    public PhonemeAnalysisResult analyzePhonemes(
            Object[][] wordSpans, 
            String[] words, 
            float[] logits, 
            int vocabSize) {
        
        if (nativeHandle == 0) {
            throw new IllegalStateException("PhonemeAnalyzer已被销毁");
        }
        
        return nativeAnalyzePhonemes(nativeHandle, wordSpans, words, logits, vocabSize);
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }
    
    // 本地方法
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native PhonemeAnalysisResult nativeAnalyzePhonemes(
            long handle, Object[][] wordSpans, String[] words, float[] logits, int vocabSize);
}