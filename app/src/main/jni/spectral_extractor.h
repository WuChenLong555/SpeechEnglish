#ifndef SPECTRAL_EXTRACTOR_H
#define SPECTRAL_EXTRACTOR_H

#include "feature_extractor.h"
#include "kiss_fft.h"
#include <memory>
#include <vector>

namespace speech {
namespace features {

class SpectralExtractor : public FeatureExtractor {
public:
    SpectralExtractor();
    ~SpectralExtractor();
    bool init(int sample_rate, int frame_size, int hop_length) override;
    bool processFrame(const float* frame, int length) override;
    std::vector<float> getFeatures() const override;
    void reset() override;

    // 获取频谱特征
    float getSpectralFlux() const { return spectral_flux_; }
    float getZeroCrossingRate() const { return zcr_; }
    float getF1() const { return f1_; }
    float getF2() const { return f2_; }
    float getFormantFitness() const { return formant_fitness_; }

private:
    // FFT相关
    kiss_fft_cfg fft_cfg_ = nullptr;
    std::vector<kiss_fft_cpx> fft_in_;
    std::vector<kiss_fft_cpx> fft_out_;
    std::vector<float> prev_spectrum_;
    std::vector<float> curr_spectrum_;
    
    // 频谱特征
    float spectral_flux_ = 0.0f;
    float zcr_ = 0.0f;
    float f1_ = 0.0f;
    float f2_ = 0.0f;
    float formant_fitness_ = 0.0f;
    
    // 共振峰检测参数
    int min_f1_ = 200;   // 最小F1频率 (Hz)
    int max_f1_ = 1000;  // 最大F1频率 (Hz)
    int min_f2_ = 800;   // 最小F2频率 (Hz)
    int max_f2_ = 3000;  // 最大F2频率 (Hz)
    
    // 内部方法
    void computeFFT(const float* frame);
    void findFormants();
    void computeSpectralFlux();
    void computeZeroCrossingRate(const float* frame, int length);
    float computeFormantFitness();
};

} // namespace features
} // namespace speech

#endif // SPECTRAL_EXTRACTOR_H 