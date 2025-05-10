#ifndef ENERGY_EXTRACTOR_H
#define ENERGY_EXTRACTOR_H

#include "feature_extractor.h"

namespace speech {
namespace features {

class EnergyExtractor : public FeatureExtractor {
public:
    bool init(int sample_rate, int frame_size, int hop_length) override;
    bool processFrame(const float* frame, int length) override;
    std::vector<float> getFeatures() const override;
    void reset() override;

    // 获取具体特征
    float getEnergy() const { return energy_; }
    float getEnergySlope() const { return energy_slope_; }
    float getEnergyAcceleration() const { return energy_acceleration_; }
    bool isSilence() const { return is_silence_; }
    float getSilenceRatio() const { return silence_ratio_; }

private:
    // 能量特征
    float energy_ = 0.0f;
    float prev_energy_ = 0.0f;
    float energy_slope_ = 0.0f;
    float prev_slope_ = 0.0f;
    float energy_acceleration_ = 0.0f;
    
    // 静默检测
    bool is_silence_ = false;
    float silence_threshold_ = 0.01f;  // 可配置
    int silence_frames_ = 0;
    int total_frames_ = 0;
    float silence_ratio_ = 0.0f;
    
    void computeEnergyFeatures(const float* frame, int length);
    void updateSilenceRatio();
};

} // namespace features
} // namespace speech

#endif // ENERGY_EXTRACTOR_H 