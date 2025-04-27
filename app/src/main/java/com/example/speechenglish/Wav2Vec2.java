package com.example.speechenglish;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.os.Build;
import android.os.Environment;

public class Wav2Vec2 {
    private static final String TAG = "Wav2Vec2";
    private long nativeHandle;
    private String[] tokens;
    private static final float THRESHOLD = 0.5f;  // 输出阈值
    private boolean isInitialized = false;
    private Context context;
    private PhonemeMapper phonemeMapper;
    private static final int MAX_AUDIO_LENGTH = 1600000; // 约100秒的16kHz音频
    private static final String BLANK_TOKEN = "<blank>";  // CTC空白标记
    private static final float LOG_THRESHOLD = -15.0f;  // 对数概率阈值
    private static final float RELAXED_LOG_THRESHOLD = -16.0f;  // 更宽松的对数概率阈值
    private static final float RELATIVE_THRESHOLD = 5.0f;  // 相对于最大值的阈值差异

    static {
        try {
            System.loadLibrary("wav2vec2");
            Log.i(TAG, "Successfully loaded wav2vec2 library");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load wav2vec2 library: " + e.getMessage());
        }
    }

    public Wav2Vec2(Context context) {
        this.context = context;
        
        // 初始化为false
        isInitialized = false;
        
        try {
            if (!checkPermissions()) {
                Log.e(TAG, "Required permissions not granted");
                throw new RuntimeException("必需的权限未授予，无法初始化");
            }

            // 初始化音素映射器
            try {
                phonemeMapper = new PhonemeMapper(context.getAssets());
                Log.i(TAG, "PhonemeMapper initialized successfully");
            } catch (IOException e) {
                Log.e(TAG, "Failed to initialize PhonemeMapper", e);
                throw new RuntimeException("Failed to initialize PhonemeMapper", e);
            }
            
            // 初始化本地库
            Log.i(TAG, "开始初始化本地库...");
            nativeHandle = init(context.getAssets());
            Log.i(TAG, "本地库初始化结果: handle=" + nativeHandle);
            
            if (nativeHandle == 0) {
                throw new RuntimeException("Failed to initialize Wav2Vec2 native handle");
            }
            
            isInitialized = true;
            Log.i(TAG, "Native initialization successful");
            
        } catch (Exception e) {
            isInitialized = false;
            Log.e(TAG, "Error initializing Wav2Vec2: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Wav2Vec2", e);
        }
    }

    private boolean checkPermissions() {
        // 基本权限列表
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.RECORD_AUDIO);

        // 根据Android版本添加适当的存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上使用细分存储权限
            requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            requiredPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            // Android 12及以下使用传统存储权限
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        // 检查所有必需权限
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing permission: " + permission);
                return false;
            }
        }

        // 检查是否需要MANAGE_EXTERNAL_STORAGE权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted, but may not be required");
                // 注意：这里我们不返回false，因为这个权限可能不是必需的
            }
        }

        return true;
    }

//    private void loadTokens(AssetManager assetManager) {
//        BufferedReader reader = null;
//        try {
//            reader = new BufferedReader(
//                new InputStreamReader(assetManager.open("model_tokens.txt")));
//            List<String> tokenList = new ArrayList<>();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                if (!line.isEmpty()) {
//                    String[] parts = line.split("\t");
//                    if (parts.length >= 2) {
//                        tokenList.add(parts[1]);
//                    }
//                }
//            }
//            tokens = tokenList.toArray(new String[0]);
//            Log.d(TAG, "Loaded " + tokens.length + " tokens");
//        } catch (IOException e) {
//            Log.e(TAG, "Error loading tokens: " + e.getMessage());
//            e.printStackTrace();
//        } finally {
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (IOException e) {
//                    Log.e(TAG, "Error closing reader: " + e.getMessage());
//                }
//            }
//        }
//    }

    public String[] processAndDecode(float[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.e(TAG, "音频数据为空或长度为0");
            return null;
        }

        // 验证音频数据
        boolean hasValidData = false;
        for (float sample : audioData) {
            if (Math.abs(sample) > 1e-6) {
                hasValidData = true;
                break;
            }
        }
        if (!hasValidData) {
            Log.e(TAG, "音频数据全为0或噪声太小");
            return null;
        }

        // 检查音频长度是否在合理范围内
        if (audioData.length > MAX_AUDIO_LENGTH) {
            Log.e(TAG, "音频数据太长: " + audioData.length + " 样本");
            return null;
        }

        try {
            float[] logits = process(audioData);
            if (logits == null) {
                Log.e(TAG, "模型处理返回空结果");
                return null;
            }

            // 验证logits数据
            for (float logit : logits) {
                if (Float.isNaN(logit) || Float.isInfinite(logit)) {
                    Log.e(TAG, "模型输出包含无效值(NaN或Infinite)");
                    return null;
                }
            }

            return decode(logits);
        } catch (Exception e) {
            Log.e(TAG, "处理音频时发生错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private float[] process(float[] audioData) {
        if (!isInitialized) {
            Log.e(TAG, "Wav2Vec2未正确初始化");
            return null;
        }

        if (nativeHandle == 0) {
            Log.e(TAG, "本地模型句柄无效");
            return null;
        }

        // 检查可用内存
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long availableMemory = maxMemory - (totalMemory - freeMemory);

        // 估算所需内存（这是一个粗略估计）
        long estimatedMemoryNeeded = audioData.length * 4L * 4L; // 假设每个样本需要4倍的处理空间
        if (availableMemory < estimatedMemoryNeeded) {
            Log.e(TAG, String.format("内存不足。需要: %d MB, 可用: %d MB", 
                estimatedMemoryNeeded / (1024*1024), 
                availableMemory / (1024*1024)));
            return null;
        }

        try {
            Log.i(TAG, "开始处理音频数据，长度: " + audioData.length);
            float[] logits = process(nativeHandle, audioData);
            
            if (logits == null) {
                Log.e(TAG, "本地处理返回空结果");
                return null;
            }

            // 验证输出维度
            if (logits.length == 0) {
                Log.e(TAG, "输出数组长度为0");
                return null;
            }

            // 获取音素数量
            String[] allPhonemes = phonemeMapper.getAllPhonemes();
            int numPhonemes = allPhonemes.length;

            // 验证输出维度是否为音素数量的整数倍
            if (logits.length % numPhonemes != 0) {
                Log.e(TAG, String.format("输出维度异常: %d 不是音素数量 %d 的整数倍", 
                    logits.length, numPhonemes));
                return null;
            }

            int timeSteps = logits.length / numPhonemes;
            Log.i(TAG, String.format("音频处理成功完成: %d个时间步 × %d个音素 = %d个logits", 
                timeSteps, numPhonemes, logits.length));

            return logits;
        } catch (Exception e) {
            Log.e(TAG, "处理音频时发生异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String[] decode(float[] logits) {
        if (logits == null) {
            Log.e(TAG, "logits为空");
            return null;
        }

        // 获取音素数量
        String[] allPhonemes = phonemeMapper.getAllPhonemes();
        int numPhonemes = allPhonemes.length;

        // 检查logits的维度是否正确
        if (logits.length % numPhonemes != 0) {
            Log.e(TAG, "logits维度与音素数量不匹配");
            return null;
        }

        int timeSteps = logits.length / numPhonemes;
        List<String> result = new ArrayList<>();
        String prevToken = null;  // 用于跟踪前一个token

        try {
            Log.i(TAG, "开始CTC解码，时间步数: " + timeSteps);

            // 对每个时间步进行解码
            for (int t = 0; t < timeSteps; t++) {
                int startIdx = t * numPhonemes;
                
                // 找出当前时间步的最大对数概率及其索引
                float maxLogit = Float.NEGATIVE_INFINITY;
                int maxIndex = -1;
                
                // 首先找出最大值
                for (int i = 0; i < numPhonemes; i++) {
                    float logit = logits[startIdx + i];
                    if (!Float.isNaN(logit) && !Float.isInfinite(logit) && logit > maxLogit) {
                        maxLogit = logit;
                        maxIndex = i;
                    }
                }

                // 如果最大值太小，跳过这个时间步
                if (maxLogit < RELAXED_LOG_THRESHOLD) {
                    continue;
                }

                // 检查是否有其他token的概率接近最大值
                boolean hasCloseCompetitor = false;
                for (int i = 0; i < numPhonemes; i++) {
                    if (i != maxIndex) {
                        float logit = logits[startIdx + i];
                        if (!Float.isNaN(logit) && !Float.isInfinite(logit) && 
                            (maxLogit - logit) < RELATIVE_THRESHOLD) {
                            hasCloseCompetitor = true;
                            break;
                        }
                    }
                }

                // 如果有接近的竞争者且最大值不够大，跳过这个时间步
                if (hasCloseCompetitor && maxLogit < LOG_THRESHOLD) {
                    continue;
                }

                // 应用CTC解码规则
                if (maxIndex >= 0 && maxIndex < numPhonemes) {
                    String currentToken = phonemeMapper.getPhoneme(maxIndex);
                    
                    // CTC解码规则：
                    // 1. 跳过空白标记
                    // 2. 合并重复的连续标记
                    // 3. 保留非重复的有效标记
                    if (currentToken != null && !currentToken.equals(BLANK_TOKEN)) {  // 规则1
                        if (!currentToken.equals(prevToken)) {  // 规则2
                            result.add(currentToken);  // 规则3
                            prevToken = currentToken;
                            Log.d(TAG, String.format("时间步 %d: 添加token '%s' (对数概率: %.3f)", 
                                t, currentToken, maxLogit));
                        }
                    } else {
                        prevToken = null;  // 重置prevToken，允许下一个相同的token出现
                    }
                }
            }

            Log.i(TAG, "CTC解码完成，得到 " + result.size() + " 个音素");
            
            // 如果结果为空，使用更宽松的阈值重试
            if (result.isEmpty()) {
                Log.w(TAG, "解码结果为空，尝试使用更宽松的阈值进行解码");
                return decodeWithRelaxedThreshold(logits);
            }

            return result.toArray(new String[0]);
        } catch (Exception e) {
            Log.e(TAG, "CTC解码过程发生错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String[] decodeWithRelaxedThreshold(float[] logits) {
        int timeSteps = logits.length / tokens.length;
        List<String> result = new ArrayList<>();
        String prevToken = null;

        try {
            for (int t = 0; t < timeSteps; t++) {
                int startIdx = t * tokens.length;
                float maxLogit = Float.NEGATIVE_INFINITY;
                int maxIndex = -1;

                // 找出最大对数概率的token
                for (int i = 0; i < tokens.length; i++) {
                    float logit = logits[startIdx + i];
                    if (!Float.isNaN(logit) && !Float.isInfinite(logit) && logit > maxLogit) {
                        maxLogit = logit;
                        maxIndex = i;
                    }
                }

                // 使用更宽松的阈值
                if (maxIndex >= 0 && maxIndex < tokens.length && maxLogit > RELAXED_LOG_THRESHOLD) {
                    String currentToken = tokens[maxIndex];
                    if (!currentToken.equals(BLANK_TOKEN) && !currentToken.equals(prevToken)) {
                        // 计算当前token的对数概率与其他token的平均差异
                        float avgDiff = 0.0f;
                        int validCount = 0;
                        for (int i = 0; i < tokens.length; i++) {
                            if (i != maxIndex) {
                                float logit = logits[startIdx + i];
                                if (!Float.isNaN(logit) && !Float.isInfinite(logit)) {
                                    avgDiff += (maxLogit - logit);
                                    validCount++;
                                }
                            }
                        }
                        avgDiff = validCount > 0 ? avgDiff / validCount : 0.0f;

                        // 只有当当前token明显优于其他token时才添加
                        if (avgDiff > RELATIVE_THRESHOLD / 2) {
                            result.add(currentToken);
                            prevToken = currentToken;
                            Log.d(TAG, String.format("备选解码 - 时间步 %d: 添加token '%s' (对数概率: %.3f, 平均差异: %.3f)", 
                                t, currentToken, maxLogit, avgDiff));
                        }
                    }
                }
            }

            Log.i(TAG, "备选解码完成，得到 " + result.size() + " 个音素");
            return result.toArray(new String[0]);
        } catch (Exception e) {
            Log.e(TAG, "备选解码过程发生错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean isInitialized() {
        return isInitialized && nativeHandle != 0;
    }

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    public void destroy() {
        if (isInitialized && nativeHandle != 0) {
            try {
                destroy(nativeHandle);
            } catch (Exception e) {
                Log.e(TAG, "Error in destroy: " + e.getMessage());
            } finally {
                nativeHandle = 0;
                isInitialized = false;
                Log.i(TAG, "Wav2Vec2 destroyed successfully");
            }
        }
    }

    private native long init(android.content.res.AssetManager assetManager);
    private native float[] process(long handle, float[] audioData);
    private native void destroy(long handle);

    private native float[] forceAlign(long handle, float[] audioData, int[] targetSequence);

    /**
     * 强制对齐的结果
     */
    public static class AlignmentResult {
        public final int[] paths;          // 每个时间步的音素ID
        public final float[] timePoints;   // 每个时间步的时间点

        public AlignmentResult(int[] paths, float[] timePoints) {
            this.paths = paths;
            this.timePoints = timePoints;
        }
    }

    public AlignmentResult processAndForceAlign(float[] audioData, int[] targetSequence) {
        if (!isInitialized()) {
            Log.e(TAG, "Wav2Vec2 is not initialized");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            Log.e(TAG, "Audio data is null or empty");
            return null;
        }

        if (targetSequence == null || targetSequence.length == 0) {
            Log.e(TAG, "Target sequence is null or empty");
            return null;
        }

        try {
            float[] result = forceAlign(nativeHandle, audioData, targetSequence);
            if (result == null) {
                Log.e(TAG, "Force alignment failed");
                return null;
            }

            // 结果数组的前半部分是paths，后半部分是时间点
            int halfLength = result.length / 2;
            int[] paths = new int[halfLength];
            float[] timePoints = new float[halfLength];

            // 分离paths和时间点
            for (int i = 0; i < halfLength; i++) {
                paths[i] = (int) result[i];
                timePoints[i] = result[i + halfLength];
            }

            return new AlignmentResult(paths, timePoints);
        } catch (Exception e) {
            Log.e(TAG, "Error in force alignment", e);
            return null;
        }
    }

    /**
     * 使用音素序列进行强制对齐
     * @param audioData 音频数据
     * @param phonemes 音素序列
     * @return 对齐结果
     */
    public AlignmentResult forceAlignPhonemes(float[] audioData, String[] phonemes) {
        if (phonemes == null || phonemes.length == 0) {
            Log.e(TAG, "音素序列为空");
            return null;
        }

        // 将音素序列转换为索引序列
        int[] indices = phonemeMapper.phonemesToIndices(phonemes);
        if (indices == null) {
            Log.e(TAG, "音素序列转换失败");
            return null;
        }

        return processAndForceAlign(audioData, indices);
    }

    /**
     * 使用文本进行强制对齐
     * @param audioData 音频数据
     * @param text 文本
     * @return 对齐结果
     */
    public AlignmentResult forceAlignText(float[] audioData, String text) {
        if (text == null || text.isEmpty()) {
            Log.e(TAG, "输入文本为空");
            return null;
        }

        // 将文本转换为音素序列
        String[] phonemes = phonemeMapper.textToPhonemes(text);
        if (phonemes == null) {
            Log.e(TAG, "文本转音素失败");
            return null;
        }

        return forceAlignPhonemes(audioData, phonemes);
    }

    /**
     * 获取音素映射器实例
     * @return PhonemeMapper实例
     */
    public PhonemeMapper getPhonemeMapper() {
        return phonemeMapper;
    }

    /**
     * 进行强制对齐测试
     * 测试音素序列: t ɑː m ɡ ɪ v z ʌ p b ɑː k s ɪ ŋ
     * @param audioData 音频数据
     * @return 对齐结果
     */
    public AlignmentResult testForceAlignment(float[] audioData) {
        // 使用音素序列进行测试
        String[] phonemes = {"t", "ɑː", "m", "ɡ", "ɪ", "v", "z", "ʌ", "p", "b", "ɑː", "k", "s", "ɪ", "ŋ"};
        return forceAlignPhonemes(audioData, phonemes);
    }
} 