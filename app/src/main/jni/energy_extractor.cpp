#include "energy_extractor.h"
#include <cmath>

namespace speech {
namespace features {

bool EnergyExtractor::init(int sample_rate, int frame_size, int hop_length) {
    sample_rate_ = sample_rate;
    frame_size_ = frame_size;
    hop_length_ = hop_length;
    reset();
    FEATURE_LOGI("EnergyExtractor initialized: sample_rate=%d, frame_size=%d, hop_length=%d", 
                sample_rate, frame_size, hop_length);
    return true;
}

bool EnergyExtractor::processFrame(const float* frame, int length) {
    if (length != frame_size_) {
        FEATURE_LOGE("Frame length mismatch: expected %d, got %d", frame_size_, length);
        return false;
    }
    
    computeEnergyFeatures(frame, length);
    updateSilenceRatio();
    return true;
}

void EnergyExtractor::computeEnergyFeatures(const float* frame, int length) {
    // 计算能量 (RMS)
    float sum = 0.0f;
    for (int i = 0; i < length; ++i) {
        sum += frame[i] * frame[i];
    }
    prev_energy_ = energy_;
    energy_ = sum / length;
    
    // 检测是否静默
    is_silence_ = energy_ < silence_threshold_;
    
    // 计算能量斜率 (一阶导数)
    prev_slope_ = energy_slope_;
    energy_slope_ = energy_ - prev_energy_;
    
    // 计算能量加速度 (二阶导数)
    energy_acceleration_ = energy_slope_ - prev_slope_;
    
    FEATURE_LOGI("Energy: %.6f, Slope: %.6f, Acceleration: %.6f, Silence: %d", 
                energy_, energy_slope_, energy_acceleration_, is_silence_ ? 1 : 0);
}

void EnergyExtractor::updateSilenceRatio() {
    total_frames_++;
    if (is_silence_) {
        silence_frames_++;
    }
    silence_ratio_ = static_cast<float>(silence_frames_) / total_frames_;
}

std::vector<float> EnergyExtractor::getFeatures() const {
    return {energy_, energy_slope_, energy_acceleration_, is_silence_ ? 1.0f : 0.0f, silence_ratio_};
}

void EnergyExtractor::reset() {
    energy_ = 0.0f;
    prev_energy_ = 0.0f;
    energy_slope_ = 0.0f;
    prev_slope_ = 0.0f;
    energy_acceleration_ = 0.0f;
    is_silence_ = false;
    silence_frames_ = 0;
    total_frames_ = 0;
    silence_ratio_ = 0.0f;
}

} // namespace features
} // namespace speech 