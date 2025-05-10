#ifndef FEATURE_EXTRACTOR_H
#define FEATURE_EXTRACTOR_H

#include <vector>
#include <android/log.h>

#define FEATURE_TAG "FeatureExtractor"
#define FEATURE_LOGI(...) __android_log_print(ANDROID_LOG_INFO, FEATURE_TAG, __VA_ARGS__)
#define FEATURE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, FEATURE_TAG, __VA_ARGS__)

namespace speech {
namespace features {

class FeatureExtractor {
public:
    virtual ~FeatureExtractor() = default;
    
    // 初始化参数
    virtual bool init(int sample_rate, int frame_size, int hop_length) = 0;
    
    // 处理一帧数据
    virtual bool processFrame(const float* frame, int length) = 0;
    
    // 获取特征结果
    virtual std::vector<float> getFeatures() const = 0;
    
    // 重置状态
    virtual void reset() = 0;

protected:
    int sample_rate_ = 0;
    int frame_size_ = 0;
    int hop_length_ = 0;
};

} // namespace features
} // namespace speech

#endif // FEATURE_EXTRACTOR_H 