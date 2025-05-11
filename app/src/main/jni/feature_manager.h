#ifndef FEATURE_MANAGER_H
#define FEATURE_MANAGER_H

#include "energy_extractor.h"
#include "pitch_extractor.h"
#include "spectral_extractor.h"

namespace speech {
namespace features {

class FeatureManager {
public:
    bool init(int sample_rate, int frame_size, int hop_length);
    bool processFrame(const float* frame, int length);
    
    // 获取所有特征
    struct Features {
        // 能量特征
        float energy;
        float energy_slope;
        float energy_acceleration;
        bool is_silence;
        float silence_ratio;
        
        // 基频特征
        float pitch;
        float pitch_slope;
        float pitch_acceleration;
        
        // 频谱特征
        float spectral_flux;
        float zcr;
        float f1;
        float f2;
        float formant_fitness;
    };
    
    Features getFeatures() const;
    void reset();
    
    // 连读判断
    bool isConnectedSpeech() const;
    
    // 设置连读判断阈值
    void setThresholds(
        float energy_threshold = 0.01f,
        float silence_ratio_threshold = 0.2f,
        float energy_slope_threshold = 0.05f,
        float energy_acceleration_threshold = 0.02f,
        float pitch_slope_threshold = 10.0f,
        float spectral_flux_threshold = 0.5f,
        float formant_fitness_threshold = 0.6f
    );

private:
    EnergyExtractor energy_extractor_;
    PitchExtractor pitch_extractor_;
    SpectralExtractor spectral_extractor_;
    
    // 连读判断阈值
    float energy_threshold_ = 0.01f;
    float silence_ratio_threshold_ = 0.2f;
    float energy_slope_threshold_ = 0.05f;
    float energy_acceleration_threshold_ = 0.02f;
    float pitch_slope_threshold_ = 10.0f;
    float spectral_flux_threshold_ = 0.5f;
    float formant_fitness_threshold_ = 0.6f;
};

} // namespace features
} // namespace speech

#endif // FEATURE_MANAGER_H 