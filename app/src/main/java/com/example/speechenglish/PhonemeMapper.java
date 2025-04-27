package com.example.speechenglish;

import android.content.res.AssetManager;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PhonemeMapper {
    private static final String TAG = "PhonemeMapper";
    private final Map<String, Integer> phonemeToIndex;
    private final Map<Integer, String> indexToPhoneme;
    private static final String TOKENS_FILE = "model_tokens.txt";

    public PhonemeMapper(AssetManager assetManager) throws IOException {
        phonemeToIndex = new HashMap<>();
        indexToPhoneme = new HashMap<>();
        loadTokens(assetManager);
    }

    private void loadTokens(AssetManager assetManager) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetManager.open(TOKENS_FILE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 2) {
                        int index = Integer.parseInt(parts[0]);
                        String phoneme = parts[1];
                        phonemeToIndex.put(phoneme, index);
                        indexToPhoneme.put(index, phoneme);
                    }
                }
            }
            Log.i(TAG, "加载了 " + phonemeToIndex.size() + " 个音素映射");
        } catch (IOException e) {
            Log.e(TAG, "加载音素映射失败", e);
            throw e;
        }
    }

    /**
     * 将音素序列转换为索引序列
     * @param phonemes 音素序列
     * @return 索引序列，如果有未知音素则返回null
     */
    public int[] phonemesToIndices(String[] phonemes) {
        if (phonemes == null || phonemes.length == 0) {
            return null;
        }

        List<Integer> indices = new ArrayList<>();
        List<String> unknownPhonemes = new ArrayList<>();

        for (String phoneme : phonemes) {
            Integer index = phonemeToIndex.get(phoneme);
            if (index != null) {
                indices.add(index);
            } else {
                unknownPhonemes.add(phoneme);
            }
        }

        if (!unknownPhonemes.isEmpty()) {
            Log.w(TAG, "发现未知音素: " + String.join(", ", unknownPhonemes));
            return null;
        }

        // 转换为基本类型数组
        int[] result = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            result[i] = indices.get(i);
        }
        return result;
    }

    /**
     * 将索引序列转换为音素序列
     * @param indices 索引序列
     * @return 音素序列，如果有未知索引则返回null
     */
    public String[] indicesToPhonemes(int[] indices) {
        if (indices == null || indices.length == 0) {
            return null;
        }

        List<String> phonemes = new ArrayList<>();
        List<Integer> unknownIndices = new ArrayList<>();

        for (int index : indices) {
            String phoneme = indexToPhoneme.get(index);
            if (phoneme != null) {
                phonemes.add(phoneme);
            } else {
                unknownIndices.add(index);
            }
        }

        if (!unknownIndices.isEmpty()) {
            Log.w(TAG, "发现未知索引: " + unknownIndices);
            return null;
        }

        return phonemes.toArray(new String[0]);
    }

    /**
     * 将文本转换为音素序列
     * @param text 输入文本
     * @return 音素序列数组
     */
    public String[] textToPhonemes(String text) {
        // TODO: 实现文本到音素的转换
        // 这里需要实现具体的文本到音素的转换逻辑
        // 可以使用第三方库或自定义规则
        throw new UnsupportedOperationException("文本到音素转换功能尚未实现");
    }

    /**
     * 检查音素是否存在于词表中
     * @param phoneme 要检查的音素
     * @return 是否存在
     */
    public boolean hasPhoneme(String phoneme) {
        return phonemeToIndex.containsKey(phoneme);
    }

    /**
     * 获取所有支持的音素列表
     * @return 音素数组
     */
    public String[] getAllPhonemes() {
        return phonemeToIndex.keySet().toArray(new String[0]);
    }

    /**
     * 获取音素的索引
     * @param phoneme 音素
     * @return 索引，如果不存在返回-1
     */
    public int getPhonemeIndex(String phoneme) {
        return phonemeToIndex.getOrDefault(phoneme, -1);
    }

    /**
     * 获取索引对应的音素
     * @param index 索引
     * @return 音素，如果不存在返回null
     */
    public String getPhoneme(int index) {
        return indexToPhoneme.get(index);
    }
} 