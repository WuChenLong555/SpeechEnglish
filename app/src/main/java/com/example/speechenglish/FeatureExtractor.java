package com.example.speechenglish;

/**
 * 语音特征提取器，用于连读检测
 */
public class FeatureExtractor {
    static {
        System.loadLibrary("speech_features");
    }
    
    /**
     * 特征数据类
     */
    public static class Features {
        // 能量特征
        public float energy;
        public float energySlope;
        public float energyAcceleration;
        public boolean isSilence;
        public float silenceRatio;
        
        // 基频特征
        public float pitch;
        public float pitchSlope;
        public float pitchAcceleration;
        
        // 频谱特征
        public float spectralFlux;
        public float zcr;
        public float f1;
        public float f2;
        public float formantFitness;
        
        @Override
        public String toString() {
            return "Features{" +
                    "energy=" + energy +
                    ", energySlope=" + energySlope +
                    ", energyAcceleration=" + energyAcceleration +
                    ", isSilence=" + isSilence +
                    ", silenceRatio=" + silenceRatio +
                    ", pitch=" + pitch +
                    ", pitchSlope=" + pitchSlope +
                    ", pitchAcceleration=" + pitchAcceleration +
                    ", spectralFlux=" + spectralFlux +
                    ", zcr=" + zcr +
                    ", f1=" + f1 +
                    ", f2=" + f2 +
                    ", formantFitness=" + formantFitness +
                    '}';
        }
    }
    
    /**
     * 初始化特征提取器
     * @param sampleRate 采样率
     * @param frameSize 帧大小（必须是2的幂）
     * @param hopLength 帧移
     * @return 是否初始化成功
     */
    public native boolean nativeInit(int sampleRate, int frameSize, int hopLength);
    
    /**
     * 处理一帧音频数据
     * @param frame 音频帧数据
     * @return 提取的特征
     */
    public native Features nativeProcessFrame(float[] frame);
    
    /**
     * 判断是否为连读
     * @return 是否连读
     */
    public native boolean nativeIsConnectedSpeech();
    
    /**
     * 设置连读判断阈值
     * @param energyThreshold 能量阈值
     * @param silenceRatioThreshold 静默比例阈值
     * @param energySlopeThreshold 能量斜率阈值
     * @param energyAccelerationThreshold 能量加速度阈值
     * @param pitchSlopeThreshold 基频斜率阈值
     * @param spectralFluxThreshold 谱流量阈值
     * @param formantFitnessThreshold 共振峰拟合优度阈值
     */
    public native void nativeSetThresholds(
            float energyThreshold,
            float silenceRatioThreshold,
            float energySlopeThreshold,
            float energyAccelerationThreshold,
            float pitchSlopeThreshold,
            float spectralFluxThreshold,
            float formantFitnessThreshold);
    
    /**
     * 重置特征提取器
     */
    public native void nativeReset();
    
    /**
     * 释放资源
     */
    public native void nativeRelease();
    
    /**
     * 便捷方法：初始化特征提取器
     * @param sampleRate 采样率
     * @return 是否初始化成功
     */
    public boolean init(int sampleRate) {
        // 默认使用25ms帧长，20ms帧移
        int frameSize = nextPowerOf2((int)(sampleRate * 0.025f));
        int hopLength = (int)(sampleRate * 0.020f);
        return nativeInit(sampleRate, frameSize, hopLength);
    }
    
    /**
     * 便捷方法：处理一帧音频并判断是否连读
     * @param frame 音频帧数据
     * @return 是否连读
     */
    public boolean processAndDetect(float[] frame) {
        Features features = nativeProcessFrame(frame);
        if (features == null) {
            return false;
        }
        return nativeIsConnectedSpeech();
    }
    
    /**
     * 便捷方法：设置默认阈值
     */
    public void setDefaultThresholds() {
        nativeSetThresholds(0.01f, 0.2f, 0.05f, 0.02f, 10.0f, 0.5f, 0.6f);
    }
    
    /**
     * 计算大于等于n的最小2的幂
     */
    private static int nextPowerOf2(int n) {
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        nativeRelease();
    }
} 