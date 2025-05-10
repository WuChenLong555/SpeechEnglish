#include "spectral_extractor.h"
#include <cmath>
#include <algorithm>
#include <numeric>

namespace speech {
namespace features {

SpectralExtractor::SpectralExtractor() : fft_cfg_(nullptr) {
}

SpectralExtractor::~SpectralExtractor() {
    if (fft_cfg_) {
        kiss_fft_free(fft_cfg_);
        fft_cfg_ = nullptr;
    }
}

bool SpectralExtractor::init(int sample_rate, int frame_size, int hop_length) {
    sample_rate_ = sample_rate;
    frame_size_ = frame_size;
    hop_length_ = hop_length;
    
    // 检查帧大小是否为2的幂
    int log2_frame_size = static_cast<int>(std::log2(frame_size));
    if ((1 << log2_frame_size) != frame_size) {
        FEATURE_LOGE("Frame size must be a power of 2, got %d", frame_size);
        return false;
    }
    
    // 初始化FFT配置
    fft_cfg_ = kiss_fft_alloc(frame_size, 0, nullptr, nullptr);
    if (!fft_cfg_) {
        FEATURE_LOGE("Failed to allocate FFT configuration");
        return false;
    }
    
    // 分配内存
    fft_in_.resize(frame_size);
    fft_out_.resize(frame_size);
    curr_spectrum_.resize(frame_size / 2);
    prev_spectrum_.resize(frame_size / 2);
    
    reset();
    FEATURE_LOGI("SpectralExtractor initialized: sample_rate=%d, frame_size=%d", 
                sample_rate, frame_size);
    return true;
}

bool SpectralExtractor::processFrame(const float* frame, int length) {
    if (length != frame_size_) {
        FEATURE_LOGE("Frame length mismatch: expected %d, got %d", frame_size_, length);
        return false;
    }
    
    // 计算过零率
    computeZeroCrossingRate(frame, length);
    
    // 计算FFT
    computeFFT(frame);
    
    // 计算谱流量
    computeSpectralFlux();
    
    // 查找共振峰
    findFormants();
    
    // 计算共振峰拟合优度
    formant_fitness_ = computeFormantFitness();
    
    return true;
}

void SpectralExtractor::computeFFT(const float* frame) {
    // 将实数输入转换为复数
    for (int i = 0; i < frame_size_; ++i) {
        fft_in_[i].r = frame[i];
        fft_in_[i].i = 0.0f;
    }
    
    // 执行FFT
    kiss_fft(fft_cfg_, fft_in_.data(), fft_out_.data());
    
    // 保存上一帧的频谱
    std::copy(curr_spectrum_.begin(), curr_spectrum_.end(), prev_spectrum_.begin());
    
    // 计算幅度谱
    for (int i = 0; i < frame_size_ / 2; ++i) {
        float real = fft_out_[i].r;
        float imag = fft_out_[i].i;
        curr_spectrum_[i] = std::sqrt(real * real + imag * imag);
    }
}

void SpectralExtractor::computeSpectralFlux() {
    // 计算相邻帧频谱的欧氏距离
    float sum_squared_diff = 0.0f;
    for (int i = 0; i < frame_size_ / 2; ++i) {
        float diff = curr_spectrum_[i] - prev_spectrum_[i];
        sum_squared_diff += diff * diff;
    }
    spectral_flux_ = std::sqrt(sum_squared_diff);
    
    FEATURE_LOGI("Spectral flux: %.6f", spectral_flux_);
}

void SpectralExtractor::computeZeroCrossingRate(const float* frame, int length) {
    int zero_crossings = 0;
    for (int i = 1; i < length; ++i) {
        if ((frame[i] >= 0.0f && frame[i-1] < 0.0f) || 
            (frame[i] < 0.0f && frame[i-1] >= 0.0f)) {
            zero_crossings++;
        }
    }
    
    zcr_ = static_cast<float>(zero_crossings) / (length - 1);
    FEATURE_LOGI("Zero crossing rate: %.6f", zcr_);
}

void SpectralExtractor::findFormants() {
    // 将频率范围转换为FFT bin索引
    int f1_min_bin = min_f1_ * frame_size_ / sample_rate_;
    int f1_max_bin = max_f1_ * frame_size_ / sample_rate_;
    int f2_min_bin = min_f2_ * frame_size_ / sample_rate_;
    int f2_max_bin = max_f2_ * frame_size_ / sample_rate_;
    
    // 查找F1（第一个共振峰）
    float max_f1_amp = 0.0f;
    int f1_bin = 0;
    for (int i = f1_min_bin; i <= f1_max_bin; ++i) {
        if (curr_spectrum_[i] > max_f1_amp) {
            max_f1_amp = curr_spectrum_[i];
            f1_bin = i;
        }
    }
    f1_ = f1_bin * sample_rate_ / frame_size_;
    
    // 查找F2（第二个共振峰）
    float max_f2_amp = 0.0f;
    int f2_bin = 0;
    for (int i = f2_min_bin; i <= f2_max_bin; ++i) {
        if (curr_spectrum_[i] > max_f2_amp) {
            max_f2_amp = curr_spectrum_[i];
            f2_bin = i;
        }
    }
    f2_ = f2_bin * sample_rate_ / frame_size_;
    
    FEATURE_LOGI("F1: %.2f Hz, F2: %.2f Hz", f1_, f2_);
}

float SpectralExtractor::computeFormantFitness() {
    // 简单实现：使用共振峰周围频谱的平滑度作为拟合优度
    // 实际应用中，可以使用更复杂的方法，如线性预测系数(LPC)
    
    int f1_bin = static_cast<int>(f1_ * frame_size_ / sample_rate_);
    int f2_bin = static_cast<int>(f2_ * frame_size_ / sample_rate_);
    
    // 检查共振峰是否有效
    if (f1_bin <= 0 || f2_bin <= 0 || f1_bin >= frame_size_/2 || f2_bin >= frame_size_/2) {
        return 0.0f;
    }
    
    // 计算F1周围频谱的平滑度
    float f1_smoothness = 0.0f;
    int window = 3;  // 窗口大小
    for (int i = std::max(1, f1_bin - window); i < std::min(frame_size_/2 - 1, f1_bin + window); ++i) {
        float diff1 = std::abs(curr_spectrum_[i] - curr_spectrum_[i-1]);
        float diff2 = std::abs(curr_spectrum_[i] - curr_spectrum_[i+1]);
        f1_smoothness += (diff1 + diff2) / 2.0f;
    }
    f1_smoothness = 1.0f / (1.0f + f1_smoothness);  // 归一化，越平滑值越大
    
    // 计算F2周围频谱的平滑度
    float f2_smoothness = 0.0f;
    for (int i = std::max(1, f2_bin - window); i < std::min(frame_size_/2 - 1, f2_bin + window); ++i) {
        float diff1 = std::abs(curr_spectrum_[i] - curr_spectrum_[i-1]);
        float diff2 = std::abs(curr_spectrum_[i] - curr_spectrum_[i+1]);
        f2_smoothness += (diff1 + diff2) / 2.0f;
    }
    f2_smoothness = 1.0f / (1.0f + f2_smoothness);  // 归一化，越平滑值越大
    
    // 综合评分
    float fitness = (f1_smoothness + f2_smoothness) / 2.0f;
    FEATURE_LOGI("Formant fitness: %.6f", fitness);
    
    return fitness;
}

std::vector<float> SpectralExtractor::getFeatures() const {
    return {spectral_flux_, zcr_, f1_, f2_, formant_fitness_};
}

void SpectralExtractor::reset() {
    spectral_flux_ = 0.0f;
    zcr_ = 0.0f;
    f1_ = 0.0f;
    f2_ = 0.0f;
    formant_fitness_ = 0.0f;
    
    std::fill(prev_spectrum_.begin(), prev_spectrum_.end(), 0.0f);
    std::fill(curr_spectrum_.begin(), curr_spectrum_.end(), 0.0f);
}

} // namespace features
} // namespace speech 