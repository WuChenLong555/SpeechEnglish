package com.example.speechenglish;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * 特征提取示例
 */
public class FeatureExtractionExample {
    private static final String TAG = "FeatureExtraction";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    
    private AudioRecord audioRecord;
    private FeatureExtractor featureExtractor;
    private boolean isRunning = false;
    private Thread processingThread;
    
    /**
     * 初始化
     */
    public void init() {
        // 创建特征提取器
        featureExtractor = new FeatureExtractor();
        if (!featureExtractor.init(SAMPLE_RATE)) {
            Log.e(TAG, "Failed to initialize feature extractor");
            return;
        }
        
        // 设置默认阈值
        featureExtractor.setDefaultThresholds();
        
        // 创建音频记录器
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize * 2);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create AudioRecord: " + e.getMessage());
            return;
        }
    }
    
    /**
     * 开始处理
     */
    public void start() {
        if (audioRecord == null || featureExtractor == null) {
            Log.e(TAG, "Not initialized");
            return;
        }
        
        if (isRunning) {
            Log.w(TAG, "Already running");
            return;
        }
        
        // 重置特征提取器
        featureExtractor.nativeReset();
        
        // 开始录音
        try {
            audioRecord.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            return;
        }
        
        isRunning = true;
        
        // 创建处理线程
        processingThread = new Thread(this::processAudio);
        processingThread.start();
    }
    
    /**
     * 停止处理
     */
    public void stop() {
        isRunning = false;
        
        if (processingThread != null) {
            try {
                processingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for processing thread to finish");
            }
            processingThread = null;
        }
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop AudioRecord: " + e.getMessage());
            }
        }
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stop();
        
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
        
        if (featureExtractor != null) {
            featureExtractor.release();
            featureExtractor = null;
        }
    }
    
    /**
     * 音频处理线程
     */
    private void processAudio() {
        // 帧大小
        int frameSize = 1024; // 应该是2的幂
        float[] buffer = new float[frameSize];
        
        while (isRunning) {
            // 读取音频数据
            int result = audioRecord.read(buffer, 0, frameSize, AudioRecord.READ_BLOCKING);
            
            if (result <= 0) {
                Log.e(TAG, "Failed to read audio data: " + result);
                continue;
            }
            
            // 处理帧并检测连读
            FeatureExtractor.Features features = featureExtractor.nativeProcessFrame(buffer);
            if (features == null) {
                Log.e(TAG, "Failed to process frame");
                continue;
            }
            
            // 判断是否连读
            boolean isConnected = featureExtractor.nativeIsConnectedSpeech();
            
            // 打印结果
            Log.d(TAG, "Connected speech: " + isConnected);
            Log.d(TAG, "Features: " + features.toString());
        }
    }
} 