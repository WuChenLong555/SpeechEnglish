#ifndef FORCE_ALIGNER_H
#define FORCE_ALIGNER_H

#include <vector>
#include "net.h"
#include <android/log.h>

#define FA_TAG "ForceAligner"
#define FA_LOGI(...) __android_log_print(ANDROID_LOG_INFO, FA_TAG, __VA_ARGS__)
#define FA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, FA_TAG, __VA_ARGS__)

namespace speech {
namespace alignment {

struct AlignmentResult {
    std::vector<int> paths;      // 对齐路径（每个时间步的音素索引）
    std::vector<float> scores;   // 对齐得分
    
    struct PhonemeSegment {
        int token;            // 词表ID（非blank）
        int startFrame;       // 起始帧索引（含）
        int endFrame;         // 结束帧索引（含）
        float scoreMean;      // 片段内均值得分
    };
    std::vector<PhonemeSegment> segments; // 聚合后的音素片段
    
    bool empty() const { return paths.empty() || scores.empty(); }
    size_t size() const { return paths.size(); }
};

class ForceAligner {
public:
    // 执行强制对齐
    static AlignmentResult align(const ncnn::Mat& features, 
                               const std::vector<int>& targets,
                               int blank_token);


private:
    // 内部实现细节
    template <typename T>
    static AlignmentResult align_impl(
        const ncnn::Mat& logits,
        const std::vector<int>& targets,
        int blank_idx
    );
};

} // namespace alignment
} // namespace speech

#endif // FORCE_ALIGNER_H 