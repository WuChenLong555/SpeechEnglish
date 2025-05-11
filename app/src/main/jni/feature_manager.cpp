#include "feature_manager.h"
#include <cmath>

namespace speech {
namespace features {

bool FeatureManager::init(int sample_rate, int frame_size, int hop_length) {
    bool success = true;
    
    // 初始化所有特征提取器
    success &= energy_extractor_.init(sample_rate, frame_size, hop_length);
    success &= pitch_extractor_.init(sample_rate, frame_size, hop_length);
    success &= spectral_extractor_.init(sample_rate, frame_size, hop_length);
    
    FEATURE_LOGI("FeatureManager initialized: %s", success ? "success" : "failed");
    return success;
}

bool FeatureManager::processFrame(const float* frame, int length) {
    bool success = true;
    
    // 处理所有特征提取器
    success &= energy_extractor_.processFrame(frame, length);
    success &= pitch_extractor_.processFrame(frame, length);
    success &= spectral_extractor_.processFrame(frame, length);
    
    return success;
}

FeatureManager::Features FeatureManager::getFeatures() const {
    Features features;
    
    // 能量特征
    features.energy = energy_extractor_.getEnergy();
    features.energy_slope = energy_extractor_.getEnergySlope();
    features.energy_acceleration = energy_extractor_.getEnergyAcceleration();
    features.is_silence = energy_extractor_.isSilence();
    features.silence_ratio = energy_extractor_.getSilenceRatio();
    
    // 基频特征
    features.pitch = pitch_extractor_.getPitch();
    features.pitch_slope = pitch_extractor_.getPitchSlope();
    features.pitch_acceleration = pitch_extractor_.getPitchAcceleration();
    
    // 频谱特征
    features.spectral_flux = spectral_extractor_.getSpectralFlux();
    features.zcr = spectral_extractor_.getZeroCrossingRate();
    features.f1 = spectral_extractor_.getF1();
    features.f2 = spectral_extractor_.getF2();
    features.formant_fitness = spectral_extractor_.getFormantFitness();
    
    return features;
}

void FeatureManager::reset() {
    energy_extractor_.reset();
    pitch_extractor_.reset();
    spectral_extractor_.reset();
}

bool FeatureManager::isConnectedSpeech() const {
    Features features = getFeatures();
    
    // 连读判断逻辑：综合多个特征
    bool is_connected = true;
    
    // 1. 静默时间比较小
    is_connected &= (features.silence_ratio < silence_ratio_threshold_);
    
    // 2. 能量足够高（非静默）
    is_connected &= (features.energy > energy_threshold_);
    
    // 3. 能量变化平缓（新增）
    is_connected &= (std::abs(features.energy_slope) < energy_slope_threshold_);
    
    // 4. 能量加速度小（新增）
    is_connected &= (std::abs(features.energy_acceleration) < energy_acceleration_threshold_);
    
    // 5. 基频变化平缓
    is_connected &= (std::abs(features.pitch_slope) < pitch_slope_threshold_);
    
    // 6. 频谱变化平缓
    is_connected &= (features.spectral_flux < spectral_flux_threshold_);
    
    // 7. 共振峰过渡平滑
    is_connected &= (features.formant_fitness > formant_fitness_threshold_);
    
    FEATURE_LOGI("Connected speech detection: %s", is_connected ? "connected" : "not connected");
    
    return is_connected;
}

void FeatureManager::setThresholds(
    float energy_threshold,
    float silence_ratio_threshold,
    float energy_slope_threshold,
    float energy_acceleration_threshold,
    float pitch_slope_threshold,
    float spectral_flux_threshold,
    float formant_fitness_threshold) {
    
    energy_threshold_ = energy_threshold;
    silence_ratio_threshold_ = silence_ratio_threshold;
    energy_slope_threshold_ = energy_slope_threshold;
    energy_acceleration_threshold_ = energy_acceleration_threshold;
    pitch_slope_threshold_ = pitch_slope_threshold;
    spectral_flux_threshold_ = spectral_flux_threshold;
    formant_fitness_threshold_ = formant_fitness_threshold;
    
    FEATURE_LOGI("Thresholds updated: energy=%.4f, silence_ratio=%.4f, energy_slope=%.4f, "
                "energy_acceleration=%.4f, pitch_slope=%.4f, spectral_flux=%.4f, formant_fitness=%.4f",
                energy_threshold_, silence_ratio_threshold_, energy_slope_threshold_,
                energy_acceleration_threshold_, pitch_slope_threshold_,
                spectral_flux_threshold_, formant_fitness_threshold_);
}

} // namespace features
} // namespace speech 