#include "force_aligner.h"
#include <cmath>
#include <limits>
#include <algorithm>

namespace speech {
namespace alignment {

AlignmentResult ForceAligner::align(
    const ncnn::Mat& features,
    const std::vector<int>& targets,
    int blank_token) {
    
    const int T = features.h;  // 时间步数
    const int V = features.w;  // 词汇表大小
    const int L = targets.size();  // 目标序列长度
    const int S = 2 * L + 1;    // 状态数
    
    // 检查输入有效性
    if (T == 0 || V == 0 || L == 0) {
        return AlignmentResult();
    }

    // 计算重复的目标token数量
    int R = 0;
    for (int i = 1; i < L; i++) {
        if (targets[i] == targets[i - 1]) {
            R++;
        }
    }

    // 检查时间步是否足够
    if (T < (L + R)) {
        return AlignmentResult();
    }

    // 使用2行滚动缓冲区存储alpha值
    const float NEG_INF = -std::numeric_limits<float>::infinity();
    std::vector<std::vector<float>> alphas(2, std::vector<float>(S, NEG_INF));
    std::vector<std::vector<int8_t>> backPtr(T, std::vector<int8_t>(S, -1));
    
    // 新增：存储每个时间步的原始概率值
    std::vector<std::vector<float>> frameProbs(T, std::vector<float>(V, 0.0f));
    for (int t = 0; t < T; t++) {
        const float* frame = (const float*)features.row(t);
        for (int v = 0; v < V; v++) {
            frameProbs[t][v] = frame[v];
        }
    }

    // 初始化起始状态
    int start = T - (L + R) > 0 ? 0 : 1;
    int end = (S == 1) ? 1 : 2;
    
    // 设置第一个时间步的值
    const float* first_frame = (const float*)features.row(0);
    for (int i = start; i < end; i++) {
        int labelIdx = (i % 2 == 0) ? blank_token : targets[i / 2];
        alphas[0][i] = first_frame[labelIdx];
    }

    // 动态规划主循环
    for (int t = 1; t < T; t++) {
        const float* frame = (const float*)features.row(t);
        int curIdx = t % 2;
        int prevIdx = (t - 1) % 2;
        
        // 更新start和end边界
        if (T - t <= L + R) {
            if ((start % 2 == 1) && start/2 + 1 < L && 
                targets[start/2] != targets[start/2 + 1]) {
                start++;
            }
            start++;
        }
        if (t <= L + R) {
            if (end % 2 == 0 && end < 2*L && 
                targets[end/2 - 1] != targets[end/2]) {
                end++;
            }
            end++;
        }

        // 重置当前行
        std::fill(alphas[curIdx].begin(), alphas[curIdx].end(), NEG_INF);
        // 保存初始start值
        int startloop = start;
        // 特殊处理第一个位置
        if (start == 0) {
            alphas[curIdx][0] = alphas[prevIdx][0] + frame[blank_token];
            backPtr[t][0] = 0;
            startloop++;
        }

        // 计算其他位置
        for (int i = startloop; i < end; i++) {
            float x0 = alphas[prevIdx][i];
            float x1 = alphas[prevIdx][i - 1];
            float x2 = NEG_INF;

            int labelIdx = (i % 2 == 0) ? blank_token : targets[i / 2];

            // 检查是否可以跳过空白标签
            if (i % 2 != 0 && i > 1 && 
                (i/2 < L) && targets[i/2] != targets[i/2 - 1]) {
                x2 = alphas[prevIdx][i - 2];
            }

            // 选择最佳路径
            if (x2 > x1 && x2 > x0) {
                alphas[curIdx][i] = x2 + frame[labelIdx];
                backPtr[t][i] = 2;
            } else if (x1 > x0 && x1 > x2) {
                alphas[curIdx][i] = x1 + frame[labelIdx];
                backPtr[t][i] = 1;
            } else if (x0 > NEG_INF) {
                alphas[curIdx][i] = x0 + frame[labelIdx];
                backPtr[t][i] = 0;
            }
        }
    }

    // 回溯找出最佳路径
    AlignmentResult result;
    result.paths.resize(T);
    result.scores.resize(T);

    // 找出最后一个时间步的最佳状态
    int lastIdx = (T - 1) % 2;
    int ltrIdx = alphas[lastIdx][S-1] > alphas[lastIdx][S-2] ? S-1 : S-2;

    // 回溯
    for (int t = T - 1; t >= 0; t--) {
        int labelIdx = ltrIdx % 2 == 0 ? blank_token : targets[ltrIdx / 2];
        result.paths[t] = labelIdx;
        
        // 修复：直接使用保存的原始概率值
        result.scores[t] = frameProbs[t][labelIdx];
        
        if (t > 0) {
            ltrIdx -= backPtr[t][ltrIdx];
        }
    }

    return result;
}

} // namespace alignment
} // namespace speech 