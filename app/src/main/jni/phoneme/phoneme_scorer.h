#pragma once

#include <vector>
#include <string>
#include "liaison_rules.h"

namespace phoneme {

struct PhonemeAlignItem {
    int id;
    std::string symbol;
    float startSec;
    float endSec;
    float score; // 原始对齐分
};

struct Boundary {
    int leftIndex;          // 左音素索引
    int rightIndex;         // 右音素索引
    std::string leftPhone;
    std::string rightPhone;
    std::string rightWord;  // 供 h-dropping 使用
    std::string position;   // initial/middle/final/pause
    float startSec;         // 边界窗口起止（可等于右音素起始等）
    float endSec;
};

struct LiaisonEvent {
    LiaisonKind kind;
    float confidence; // 0-1
    int boundaryIndex; // 对应 boundaries 下标
};

struct OptimizeResult {
    std::vector<PhonemeAlignItem> updated; // 更新后的得分
    std::vector<LiaisonEvent> events;      // 连读事件
};

// 评分优化器：按 ReferencePycode/phoneme_scorer.py 逻辑更新分数
class PhonemeScorer {
public:
    explicit PhonemeScorer(const std::string& jsonConfig);
    OptimizeResult optimize(const std::vector<PhonemeAlignItem>& align,
                            const std::vector<Boundary>& boundaries) const;
private:
    // 阈值与权重（从 JSON 加载）
    float plosiveElisionThreshold_;
    float nasalizationThreshold_;
    float rewardScale_;
    float penaltyScale_;
};

} // namespace phoneme


