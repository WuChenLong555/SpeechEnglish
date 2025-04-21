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

            // 先加载词表
            loadTokens(context.getAssets());
            Log.i(TAG, "Tokens loaded successfully, count: " + (tokens != null ? tokens.length : 0));
            
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

    private void loadTokens(AssetManager assetManager) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                new InputStreamReader(assetManager.open("model_tokens.txt")));
            List<String> tokenList = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 2) {
                        tokenList.add(parts[1]);
                    }
                }
            }
            tokens = tokenList.toArray(new String[0]);
            Log.d(TAG, "Loaded " + tokens.length + " tokens");
        } catch (IOException e) {
            Log.e(TAG, "Error loading tokens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
        }
    }

    public String[] processAndDecode(float[] audioData) {
        if (!isInitialized) {
            Log.e(TAG, "Wav2Vec2 not properly initialized");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            Log.e(TAG, "Invalid audio data");
            return null;
        }

        // 检查音频数据长度是否合理
        if (audioData.length > 1600000) { // 约100秒@16kHz
            Log.e(TAG, "Audio data too long: " + audioData.length + " samples");
            return null;
        }

        // 检查音频数据值是否在合理范围内
        boolean hasValidSamples = false;
        for (float sample : audioData) {
            if (Float.isNaN(sample) || Float.isInfinite(sample)) {
                Log.e(TAG, "Invalid audio sample detected: " + sample);
                return null;
            }
            if (Math.abs(sample) > 0.0001f) {
                hasValidSamples = true;
            }
        }

        if (!hasValidSamples) {
            Log.e(TAG, "Audio data contains only silence or very low amplitude");
            return null;
        }

        try {
            Log.i(TAG, "Processing audio data of length: " + audioData.length);
            float[] logits = process(nativeHandle, audioData);
            if (logits == null) {
                Log.e(TAG, "Process returned null logits");
                return null;
            }
            
            // 检查logits的维度是否合理
            if (logits.length == 0 || logits.length % tokens.length != 0) {
                Log.e(TAG, "Invalid logits dimension: " + logits.length);
                return null;
            }
            
            // 找出每个时间步最可能的音素
            List<String> phonemes = new ArrayList<>();
            int numTokens = tokens.length;
            int timeSteps = logits.length / numTokens;
            
            Log.d(TAG, "Processing logits: timeSteps=" + timeSteps + ", numTokens=" + numTokens);
            
            for (int t = 0; t < timeSteps; t++) {
                int maxIdx = -1;
                float maxVal = Float.NEGATIVE_INFINITY;
                
                // 在当前时间步找出概率最大的音素
                for (int i = 0; i < numTokens; i++) {
                    float val = logits[t * numTokens + i];
                    // 检查logit值是否有效
                    if (Float.isNaN(val) || Float.isInfinite(val)) {
                        Log.e(TAG, "Invalid logit value at position " + (t * numTokens + i) + ": " + val);
                        continue;
                    }
                    if (val > maxVal) {
                        maxVal = val;
                        maxIdx = i;
                    }
                }
                
                // 如果概率超过阈值，添加到结果中
                if (maxVal > THRESHOLD && maxIdx >= 0 && maxIdx < tokens.length) {
                    // 跳过特殊标记（<pad>, <s>, </s>, <unk>）
                    if (maxIdx > 3) {
                        phonemes.add(tokens[maxIdx]);
                    }
                }
            }
            
            if (phonemes.isEmpty()) {
                Log.w(TAG, "No phonemes detected above threshold");
                return new String[0];
            }
            
            Log.i(TAG, "Successfully decoded " + phonemes.size() + " phonemes");
            return phonemes.toArray(new String[0]);
        } catch (Exception e) {
            Log.e(TAG, "Error in processAndDecode: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public float[] process(float[] audioData) {
        if (!isInitialized) {
            Log.e(TAG, "Cannot process: Wav2Vec2 not initialized");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            Log.e(TAG, "Invalid audio data");
            return null;
        }

        try {
            // 在调用native方法之前添加内存检查
            long requiredMemory = audioData.length * 4L; // 每个float占4字节
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
            
            if (freeMemory < requiredMemory * 2) { // 预留2倍内存空间
                Log.e(TAG, "Insufficient memory for processing. Required: " + requiredMemory + ", Free: " + freeMemory);
                return null;
            }

            return process(nativeHandle, audioData);
        } catch (Exception e) {
            Log.e(TAG, "Error in process: " + e.getMessage());
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
} 