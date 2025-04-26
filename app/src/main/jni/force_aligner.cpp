#include "force_aligner.h"
#include <limits>
#include <cmath>

namespace speech {
namespace alignment {

template <typename T>
AlignmentResult ForceAligner::align_impl(
    const ncnn::Mat& logits,
    const std::vector<int>& targets,
    int blank_idx) {
    
    const T kNegInfinity = -std::numeric_limits<T>::infinity();
    const int num_time_steps = logits.h;  // 时间步长
    const int L = targets.size();  // 目标序列长度
    const int S = 2 * L + 1;  // 状态数
    
    // 计算重复字符数
    int R = 0;
    for (int i = 1; i < L; i++) {
        if (targets[i] == targets[i - 1]) {
            ++R;
        }
    }
    
    if (num_time_steps < L + R) {
        FA_LOGE("Sequence too short: T=%d, L=%d, R=%d", num_time_steps, L, R);
        return AlignmentResult();
    }
    
    // 初始化alpha矩阵和回溯指针
    std::vector<std::vector<T>> alphas(2, std::vector<T>(S, kNegInfinity));
    std::vector<std::vector<int8_t>> backPtr(num_time_steps, std::vector<int8_t>(S, -1));
    
    // 初始化第一个时间步
    int start = num_time_steps - (L + R) > 0 ? 0 : 1;
    int end = (S == 1) ? 1 : 2;
    
    for (int i = start; i < end; i++) {
        int labelIdx = (i % 2 == 0) ? blank_idx : targets[i / 2];
        alphas[0][i] = logits.row(0)[labelIdx];
    }
    
    // 动态规划
    for (int t = 1; t < num_time_steps; t++) {
        if (num_time_steps - t <= L + R) {
            if ((start % 2 == 1) && targets[start / 2] != targets[start / 2 + 1]) {
                start = start + 1;
            }
            start = start + 1;
        }
        if (t <= L + R) {
            if (end % 2 == 0 && end < 2 * L && targets[end / 2 - 1] != targets[end / 2]) {
                end = end + 1;
            }
            end = end + 1;
        }
        
        int curIdxOffset = t % 2;
        int prevIdxOffset = (t - 1) % 2;
        
        // 重置当前时间步的alpha值
        std::fill(alphas[curIdxOffset].begin(), alphas[curIdxOffset].end(), kNegInfinity);
        
        // 处理blank转换
        if (start == 0) {
            alphas[curIdxOffset][0] = alphas[prevIdxOffset][0] + logits.row(t)[blank_idx];
            backPtr[t][0] = 0;
            start += 1;
        }
        
        // 处理其他转换
        for (int i = start; i < end; i++) {
            T x0 = alphas[prevIdxOffset][i];
            T x1 = alphas[prevIdxOffset][i - 1];
            T x2 = kNegInfinity;
            
            int labelIdx = (i % 2 == 0) ? blank_idx : targets[i / 2];
            
            if (i % 2 != 0 && i != 1 && targets[i / 2] != targets[i / 2 - 1]) {
                x2 = alphas[prevIdxOffset][i - 2];
            }
            
            T result;
            if (x2 > x1 && x2 > x0) {
                result = x2;
                backPtr[t][i] = 2;
            } else if (x1 > x0 && x1 > x2) {
                result = x1;
                backPtr[t][i] = 1;
            } else {
                result = x0;
                backPtr[t][i] = 0;
            }
            
            alphas[curIdxOffset][i] = result + logits.row(t)[labelIdx];
        }
    }
    
    // 回溯最优路径
    AlignmentResult result;
    result.paths.resize(num_time_steps);
    result.scores.resize(num_time_steps);
    
    int idx1 = (num_time_steps - 1) % 2;
    int ltrIdx = alphas[idx1][S - 1] > alphas[idx1][S - 2] ? S - 1 : S - 2;
    
    for (int t = num_time_steps - 1; t >= 0; t--) {
        int lbl_idx = ltrIdx % 2 == 0 ? blank_idx : targets[ltrIdx / 2];
        result.paths[t] = lbl_idx;
        result.scores[t] = logits.row(t)[lbl_idx];
        ltrIdx -= backPtr[t][ltrIdx];
    }
    
    return result;
}

AlignmentResult ForceAligner::align(
    const ncnn::Mat& logits,
    const std::vector<int>& targets,
    int blank_idx) {
    
    if (logits.empty() || targets.empty()) {
        FA_LOGE("Empty input: logits.empty=%d, targets.empty=%d", 
             logits.empty(), targets.empty());
        return AlignmentResult();
    }
    
    FA_LOGI("Computing force alignment: T=%d, L=%zu", logits.h, targets.size());
    
    return align_impl<float>(logits, targets, blank_idx);
}

} // namespace alignment
} // namespace speech 