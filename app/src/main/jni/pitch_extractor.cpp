#include "pitch_extractor.h"
#include <cmath>
#include <algorithm>

namespace speech {
namespace features {

bool PitchExtractor::init(int sample_rate, int frame_size, int hop_length) {
    sample_rate_ = sample_rate;
    frame_size_ = frame_size;
    hop_length_ = hop_length;
    autocorr_.resize(frame_size);
    reset();
    FEATURE_LOGI("PitchExtractor initialized: sample_rate=%d, frame_size=%d, hop_length=%d", 
                sample_rate, frame_size, hop_length);
    return true;
}

bool PitchExtractor::processFrame(const float* frame, int length) {
    if (length != frame_size_) {
        FEATURE_LOGE("Frame length mismatch: expected %d, got %d", frame_size_, length);
        return false;
    }
    
    computePitch(frame, length);
    return true;
}

void PitchExtractor::computePitch(const float* frame, int length) {
    // 计算自相关
    for (int lag = 0; lag < length; ++lag) {
        float sum = 0.0f;
        for (int i = 0; i < length - lag; ++i) {
            sum += frame[i] * frame[i + lag];
        }
        autocorr_[lag] = sum;
    }
    
    // 归一化自相关
    float zero_lag = autocorr_[0];
    if (zero_lag > 0) {
        for (int i = 0; i < length; ++i) {
            autocorr_[i] /= zero_lag;
        }
    }
    
    // 在合理范围内寻找自相关峰值
    int min_lag = static_cast<int>(sample_rate_ / max_freq_);
    int max_lag = static_cast<int>(sample_rate_ / min_freq_);
    
    // 确保在合理范围内
    min_lag = std::max(min_lag, 1);
    max_lag = std::min(max_lag, length - 1);
    
    float max_corr = -1.0f;
    int best_lag = 0;
    
    // 寻找峰值
    for (int lag = min_lag; lag <= max_lag; ++lag) {
        if (autocorr_[lag] > max_corr) {
            max_corr = autocorr_[lag];
            best_lag = lag;
        }
    }
    
    // 如果找到有效峰值
    if (max_corr > 0.3f) {  // 设置一个阈值，避免噪声干扰
        prev_pitch_ = pitch_;
        pitch_ = static_cast<float>(sample_rate_) / static_cast<float>(best_lag);
        
        // 计算基频斜率 (一阶导数)
        prev_slope_ = pitch_slope_;
        pitch_slope_ = pitch_ - prev_pitch_;
        
        // 计算基频加速度 (二阶导数)
        pitch_acceleration_ = pitch_slope_ - prev_slope_;
        
        FEATURE_LOGI("Pitch: %.2f Hz, Slope: %.2f, Acceleration: %.2f", 
                    pitch_, pitch_slope_, pitch_acceleration_);
    } else {
        // 如果没有找到有效峰值，可能是无声段
        FEATURE_LOGI("No valid pitch found, possibly unvoiced segment");
        // 保持前一个值，或者设置为0
        pitch_ = 0.0f;
        pitch_slope_ = 0.0f;
        pitch_acceleration_ = 0.0f;
    }
}

std::vector<float> PitchExtractor::getFeatures() const {
    return {pitch_, pitch_slope_, pitch_acceleration_};
}

void PitchExtractor::reset() {
    pitch_ = 0.0f;
    prev_pitch_ = 0.0f;
    pitch_slope_ = 0.0f;
    prev_slope_ = 0.0f;
    pitch_acceleration_ = 0.0f;
    std::fill(autocorr_.begin(), autocorr_.end(), 0.0f);
}

} // namespace features
} // namespace speech 