#ifndef WAV2VEC2_H
#define WAV2VEC2_H

#include "net.h"
#include "wav2vec2_model.h"
#include <vector>
#include <android/asset_manager.h>

class Wav2Vec2 {
public:
    ncnn::Net net;
    ncnn::VulkanDevice* vkdev = nullptr;
    ncnn::Net net_gpu;
    bool initialized = false;
    bool useGPU = false;
    ncnn::Mat lastOutput;  // 保存最后的输出结果

    // 音频预处理参数
    static constexpr int SAMPLE_RATE = wav2vec2::ModelConfig::SAMPLE_RATE;
    static constexpr int FRAME_LENGTH = 400;  // 25ms at 16kHz
    static constexpr int FRAME_SHIFT = 320;   // 20ms at 16kHz
    static constexpr int FEATURE_DIM = wav2vec2::ModelConfig::FEATURE_DIM;

    // 构造函数和析构函数声明为默认
    Wav2Vec2() = default;
    ~Wav2Vec2() = default;

    // 初始化方法
    bool init(AAssetManager* assetManager);
    
    // 推理方法
    bool processInference(const float* audioData, int audioLength);
    float* process(const float* audioData, int audioLength);
    
    // 获取输出
    const ncnn::Mat& getLastOutput() const;
    
    // 销毁方法
    void destroy();
    bool isInitialized() const;

private:
    // 音频标准化函数
    std::vector<float> normalizeAudio(const float* audioData, int length);
    std::vector<float> normalizeAudioOptimized(const float* audioData, int length);
};

#endif // WAV2VEC2_H