#ifndef PITCH_EXTRACTOR_H
#define PITCH_EXTRACTOR_H

#include "feature_extractor.h"
#include <vector>

namespace speech {
namespace features {

class PitchExtractor : public FeatureExtractor {
public:
    bool init(int sample_rate, int frame_size, int hop_length) override;
    bool processFrame(const float* frame, int length) override;
    std::vector<float> getFeatures() const override;
    void reset() override;

    // 获取具体特征
    float getPitch() const { return pitch_; }
    float getPitchSlope() const { return pitch_slope_; }
    float getPitchAcceleration() const { return pitch_acceleration_; }
    
    // 设置基频范围
    void setFrequencyRange(float min_freq, float max_freq) {
        min_freq_ = min_freq;
        max_freq_ = max_freq;
    }

private:
    // 基频特征
    float pitch_ = 0.0f;
    float prev_pitch_ = 0.0f;
    float pitch_slope_ = 0.0f;
    float prev_slope_ = 0.0f;
    float pitch_acceleration_ = 0.0f;
    
    // 自相关计算
    std::vector<float> autocorr_;
    float min_freq_ = 50.0f;   // 最小基频 (Hz)
    float max_freq_ = 400.0f;  // 最大基频 (Hz)
    
    void computePitch(const float* frame, int length);
};

} // namespace features
} // namespace speech

#endif // PITCH_EXTRACTOR_H 