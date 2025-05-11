package com.example.speechenglish;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

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
    private Context context;
    
    public FeatureExtractionExample(Context context) {
        this.context = context;
    }
    
    /**
     * 检查麦克风权限
     * @return 是否有权限
     */
    public boolean checkMicrophonePermission() {
        return context != null && 
               ContextCompat.checkSelfPermission(context, 
                   Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 初始化
     * @return 是否初始化成功
     */
    public boolean init() {
        // 检查麦克风权限
        if (!checkMicrophonePermission()) {
            Log.e(TAG, "No microphone permission");
            return false;
        }
        
        // 创建特征提取器
        featureExtractor = new FeatureExtractor();
        if (!featureExtractor.init(SAMPLE_RATE)) {
            Log.e(TAG, "Failed to initialize feature extractor");
            return false;
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
                    
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create AudioRecord: " + e.getMessage());
            return false;
        }
        
        return true;
    }
    
    /**
     * 开始处理
     * @return 是否成功启动
     */
    public boolean start() {
        if (audioRecord == null || featureExtractor == null) {
            Log.e(TAG, "Not initialized");
            return false;
        }
        
        if (isRunning) {
            Log.w(TAG, "Already running");
            return true;
        }
        
        // 再次检查麦克风权限（可能在初始化后被撤销）
        if (!checkMicrophonePermission()) {
            Log.e(TAG, "No microphone permission");
            return false;
        }
        
        // 重置特征提取器
        featureExtractor.nativeReset();
        
        // 开始录音
        try {
            audioRecord.startRecording();
            
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Failed to start recording");
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when starting recording: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            return false;
        }
        
        isRunning = true;
        
        // 创建处理线程
        processingThread = new Thread(this::processAudio);
        processingThread.start();
        
        return true;
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
        
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
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
            try {
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
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception during audio processing: " + e.getMessage());
                isRunning = false;
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error during audio processing: " + e.getMessage());
                // 继续处理，除非是严重错误
                if (e instanceof IllegalStateException) {
                    isRunning = false;
                    break;
                }
            }
        }
    }
} 