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

    // 初始化alpha矩阵
    const float NEG_INF = -std::numeric_limits<float>::infinity();
    std::vector<std::vector<float>> alpha(T, std::vector<float>(L * 2 + 1, NEG_INF));
    std::vector<std::vector<int>> backpointers(T, std::vector<int>(L * 2 + 1, -1));

    // 初始化第一个时间步
    const float* first_frame = (const float*)features.row(0);
    alpha[0][0] = first_frame[blank_token];
    if (L > 0) {
        alpha[0][1] = first_frame[targets[0]];
    }

    // 动态规划
    for (int t = 1; t < T; t++) {
        const float* frame = (const float*)features.row(t);
        
        for (int s = 0; s < 2 * L + 1; s++) {
            float max_score = NEG_INF;
            int best_prev = -1;

            // 计算可能的转移
            for (int prev_s = std::max(0, s - 2); prev_s <= s; prev_s++) {
                if (alpha[t-1][prev_s] == NEG_INF) continue;

                float score = alpha[t-1][prev_s];
                if (score > max_score) {
                    max_score = score;
                    best_prev = prev_s;
                }
            }

            if (best_prev >= 0) {
                int token = (s % 2 == 0) ? blank_token : targets[s / 2];
                alpha[t][s] = max_score + frame[token];
                backpointers[t][s] = best_prev;
            }
        }
    }

    // 回溯找出最佳路径
    AlignmentResult result;
    result.paths.resize(T);
    result.scores.resize(T);

    // 找出最后一个时间步的最佳状态
    int s = 2 * L;
    float max_final_score = alpha[T-1][s];
    for (int i = 0; i < 2 * L + 1; i++) {
        if (alpha[T-1][i] > max_final_score) {
            max_final_score = alpha[T-1][i];
            s = i;
        }
    }

    // 回溯
    for (int t = T - 1; t >= 0; t--) {
        int token = (s % 2 == 0) ? blank_token : targets[s / 2];
        result.paths[t] = token;
        result.scores[t] = alpha[t][s];
        
        if (t > 0) {
            s = backpointers[t][s];
        }
    }

    return result;
}

} // namespace alignment
} // namespace speech 