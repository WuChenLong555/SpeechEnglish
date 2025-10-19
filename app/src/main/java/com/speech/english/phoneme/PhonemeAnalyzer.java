package com.speech.english.phoneme;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
     * 从assets读取文本文件内容
     * 
     * @param context Android上下文
     * @param fileName 文件名
     * @return 文件内容字符串
     */
    private String readAssetText(Context context, String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
    
    /**
     * 从JSON文件加载单词音素个数数组
     * 
     * @param context Android上下文
     * @param jsonPath JSON文件路径（相对于assets）
     * @return 每个单词的音素个数数组
     */
    public int[] loadWordPhoneCounts(Context context, String jsonPath) throws Exception {
        String jsonContent = readAssetText(context, jsonPath);
        JSONObject jsonObj = new JSONObject(jsonContent);
        JSONArray originalPhones = jsonObj.getJSONArray("original_phones");
        
        int[] wordPhoneCounts = new int[originalPhones.length()];
        for (int i = 0; i < originalPhones.length(); i++) {
            JSONArray phoneArray = originalPhones.getJSONArray(i);
            wordPhoneCounts[i] = phoneArray.length();
        }
        
        return wordPhoneCounts;
    }
    
    /**
     * 分析音素并返回错误和优化后的得分
     * 
     * @param words 原始文本单词
     * @param targets 目标音素序列
     * @param audioData 音频数据（16kHz采样率）
     * @param blankId blank token的ID
     * @param wordPhoneCounts 每个单词的音素个数
     * @param assetManager Android AssetManager用于加载模型
     * @return 音素分析结果
     */
    public PhonemeAnalysisResult analyzePhonemes(
            String[] words, 
            int[] targets,
            float[] audioData,
            int blankId,
            int[] wordPhoneCounts,
            android.content.res.AssetManager assetManager) {
        
        if (nativeHandle == 0) {
            throw new IllegalStateException("PhonemeAnalyzer已被销毁");
        }
        
        return nativeAnalyzePhonemes(nativeHandle, words, targets, audioData, blankId, wordPhoneCounts, assetManager);
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
            long handle, String[] words, int[] targets, float[] audioData, int blankId, int[] wordPhoneCounts, android.content.res.AssetManager assetManager);
}