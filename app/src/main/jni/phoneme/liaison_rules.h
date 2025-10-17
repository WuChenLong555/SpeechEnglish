#pragma once

#include <string>
#include "phoneme_sets.h"

namespace phoneme {

enum class LiaisonKind {
    NONE,
    VOWEL_VOWEL, CONSONANT_VOWEL, PLOSIVE_ELISION, H_DROPPING,
    R_LINKING, J_LINKING, W_LINKING, SAME_CONSONANT, SH_LINKING, Y_LINKING,
    LIQUID_VOWEL, NASAL_VOWEL, FRICATIVE_VOWEL, PLOSIVE_VOWEL, NASALIZATION, SCHWA
};

struct LiaisonDecision {
    LiaisonKind kind;
    float score;     // 证据强度（可选）
    bool triggered;  // 是否触发
};

// 规则判定：等价 Java 逻辑的 C++ 实现（必要时可加入特征证据）
LiaisonKind checkLiaison(const std::string& phoneme1,
                         const std::string& phoneme2,
                         const std::string& word2,
                         const std::string& position);

inline bool checkHDropping(const std::string& word,
                           const std::string& prevPhoneme,
                           const std::string& position) {
    if (word.empty() || prevPhoneme.empty()) return false;
    if (position == "initial" || position == "pause") return false;
    return isConsonant(prevPhoneme) && (
        word == "he" || word == "his" || word == "him" || word == "her" ||
        word == "here" || word == "house" ||
        word == "him," || word == "her," || word == "he," || word == "here,"
    );
}

} // namespace phoneme


