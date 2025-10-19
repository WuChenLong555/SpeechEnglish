#include "phoneme_analyzer.h"
#include "phoneme_sets.h"
#include "token_mapper.h"
#include <algorithm>
#include <cmath>
#include <unordered_set>
#include <__numeric/accumulate.h>

namespace phoneme {

// 常量定义
constexpr float SCORE_THRESHOLD = 0.6f;
constexpr float CONFIDENCE_BOOST = 0.2f;
constexpr float MAX_SCORE = 1.0f;

PhonemeAnalyzer::PhonemeAnalyzer() {
    initAssimilationMap();
}

// 从logits中获取特定音素的概率
float PhonemeAnalyzer::getPhonemeLogitScore(
    const std::string& phoneme,
    int startFrame,
    int endFrame,
    const std::vector<float>& logits,
    int vocabSize) {
    
    // 获取音素对应的ID
    int phonemeId = tokenMapper_.getIdByToken(phoneme);
    if (phonemeId < 0) {
        // 如果找不到对应的ID，返回0概率
        return 0.0f;
    }
    
    // 确保帧范围有效
    startFrame = std::max(0, startFrame);
    endFrame = std::min(static_cast<int>(logits.size() / vocabSize), endFrame);
    
    if (startFrame >= endFrame) {
        return 0.0f;
    }
    
    // 计算该音素在指定时间段内的平均概率
    float totalProb = 0.0f;
    int frameCount = 0;
    
    for (int frame = startFrame; frame < endFrame; frame++) {
        // 计算当前帧中该音素的概率
        int offset = frame * vocabSize + phonemeId;
        if (offset < logits.size()) {
            // logits中存储的是对数概率，需要转换为实际概率
            float prob = std::exp(logits[offset]);
            totalProb += prob;
            frameCount++;
        }
    }
    
    // 返回平均概率
    return (frameCount > 0) ? (totalProb / frameCount) : 0.0f;
}

PhonemeAnalyzer::~PhonemeAnalyzer() {
}

void PhonemeAnalyzer::initAssimilationMap() {
    // Y连读的音素映射
    assimilationMap_["s"] = "ʃ";
    assimilationMap_["z"] = "ʒ";
    assimilationMap_["t"] = "tʃ";
    assimilationMap_["d"] = "dʒ";
}

PhonemeAnalysisResult PhonemeAnalyzer::analyzePhonemes(
    const std::vector<std::vector<PhonemeSpan>>& wordSpans,
    const std::vector<std::string>& words,
    const std::vector<float>& logits,
    int vocabSize) {
    
    PhonemeAnalysisResult result;
    std::vector<PhonemeError> errors;
    std::vector<float> updatedScores;
    
    // 预分配内存，减少重新分配
    const size_t estimatedErrorCount = wordSpans.size() * 3; // 估计每个单词平均3个错误
    errors.reserve(estimatedErrorCount);
    
    // 预计算总音素数
    size_t totalPhonemes = 0;
    for (const auto& wordSpan : wordSpans) {
        totalPhonemes += wordSpan.size();
    }
    updatedScores.reserve(totalPhonemes);
    
    // 缓存常用单词，避免重复字符串比较
    std::unordered_set<std::string> weakFormWords = {"for", "your", "you're", "to", "and", "of", "a", "the", "in", "that", "is", "was", "his", "her", "him", "he", "here"};
    std::unordered_set<std::string> hDroppingWords = {"his", "him", "her", "he", "here", "house"};
    
    // 遍历每个单词
    for (size_t wordIdx = 0; wordIdx < wordSpans.size(); wordIdx++) {
        const auto& wordSpan = wordSpans[wordIdx];
        const std::string& currentWord = (wordIdx < words.size()) ? words[wordIdx] : "";
        const bool isWeakFormWord = weakFormWords.find(currentWord) != weakFormWords.end();
        const bool isHDroppingWord = hDroppingWords.find(currentWord) != hDroppingWords.end();
        
        // 遍历单词中的每个音素
        for (size_t phoneIdx = 0; phoneIdx < wordSpan.size(); phoneIdx++) {
            auto& phoneSpan = const_cast<PhonemeSpan&>(wordSpan[phoneIdx]);
            
            // 如果分数已经很高，则跳过
            if (phoneSpan.score > SCORE_THRESHOLD) {
                updatedScores.push_back(phoneSpan.score);
                continue;
            }
            
            // 获取当前音素和下一个音素
            const std::string& targetPhoneme = phoneSpan.text;
            std::string targetPhonemeNext;
            PhonemeSpan* nextPhoneSpan = nullptr;
            
            // 获取下一个音素（可能在下一个单词中）
            if (phoneIdx + 1 < wordSpan.size()) {
                targetPhonemeNext = wordSpan[phoneIdx + 1].text;
                nextPhoneSpan = const_cast<PhonemeSpan*>(&wordSpan[phoneIdx + 1]);
            } else if (wordIdx + 1 < wordSpans.size() && !wordSpans[wordIdx + 1].empty()) {
                targetPhonemeNext = wordSpans[wordIdx + 1][0].text;
                nextPhoneSpan = const_cast<PhonemeSpan*>(&wordSpans[wordIdx + 1][0]);
            }
            
            // 检查连读类型
            LiaisonKind liaisonKind = LiaisonKind::NONE;
            if (!targetPhonemeNext.empty()) {
                std::string nextWord = (wordIdx + 1 < words.size()) ? words[wordIdx + 1] : "";
                liaisonKind = checkLiaison(targetPhoneme, targetPhonemeNext, nextWord, "middle");
            }
            
            // 时间信息
            float timeStart = phoneSpan.start;
            float timeEnd = phoneSpan.end;
            
            // 根据连读类型处理不同的情况
            bool processed = false;
            
            // 处理h音素的特殊情况
            if (targetPhoneme == "h" && isHDroppingWord) {
                // h音素在特定单词中可以省略
                phoneSpan.score = 0.9;
                errors.emplace_back(
                    ErrorType::DELETION_H_DROPPING,
                    phoneIdx,
                    targetPhoneme,
                    nullptr,
                    phoneSpan.score,
                    timeStart,
                    timeEnd
                );
                processed = true;
            } else {
                switch (liaisonKind) {
                    case LiaisonKind::Y_LINKING:
                        if (nextPhoneSpan) {
                            handleYLinking(phoneSpan, *nextPhoneSpan, targetPhoneme, targetPhonemeNext, 
                                          logits, vocabSize, errors, wordIdx, phoneIdx);
                            processed = true;
                        }
                        break;
                        
                    case LiaisonKind::SH_LINKING:
                        handleSHLinking(phoneSpan, targetPhoneme, logits, vocabSize, errors, wordIdx, phoneIdx, 
                                       timeStart, timeEnd);
                        processed = true;
                        break;
                        
                    case LiaisonKind::NASALIZATION:
                        handleNasalization(phoneSpan, targetPhoneme, targetPhonemeNext, 
                                          logits, vocabSize, errors, wordIdx, phoneIdx, timeStart, timeEnd);
                        processed = true;
                        break;
                        
                    case LiaisonKind::PLOSIVE_ELISION:
                        // 爆破音省略
                        phoneSpan.score += CONFIDENCE_BOOST;
                        errors.emplace_back(
                            ErrorType::DELETION_PLOSIVE_ELISION,
                            phoneIdx,
                            targetPhoneme,
                            nullptr,
                            phoneSpan.score,
                            timeStart,
                            timeEnd
                        );
                        processed = true;
                        break;
                        
                    case LiaisonKind::SAME_CONSONANT:
                        // 相同辅音连读
                        if (nextPhoneSpan && nextPhoneSpan->score > SCORE_THRESHOLD) {
                            phoneSpan.score = nextPhoneSpan->score;
                            errors.emplace_back(
                                ErrorType::DELETION_SAME_CONSONANT,
                                phoneIdx,
                                targetPhoneme,
                                nullptr,
                                phoneSpan.score,
                                timeStart,
                                timeEnd
                            );
                            processed = true;
                        }
                        break;
                        
                    default:
                        break;
                }
            }
            
            // 处理弱读形式
            if (!processed && isWeakFormWord) {
                handleWeakForms(phoneSpan, targetPhoneme, currentWord, logits, vocabSize,
                               errors, wordIdx, phoneIdx, timeStart, timeEnd);
            }
            
            updatedScores.push_back(phoneSpan.score);
        }
    }
    
    // 计算优化后的评分
    calculateOptimizedScore(updatedScores, errors, result);
    
    return result;
}

void PhonemeAnalyzer::calculateOptimizedScore(
    const std::vector<float>& updatedScores, 
    const std::vector<PhonemeError>& errors,
    PhonemeAnalysisResult& result) {
    
    // 基础评分：所有音素得分的平均值
    float baseScore = 0.0f;
    if (!updatedScores.empty()) {
        baseScore = std::accumulate(updatedScores.begin(), updatedScores.end(), 0.0f) / updatedScores.size();
    }
    
    // 错误权重
    float errorPenalty = 0.0f;
    
    // 统计不同类型的错误数量
    int deletionCount = 0;
    int substitutionCount = 0;
    int validLiaisonCount = 0;
    int weakFormCount = 0;
    
    for (const auto& error : errors) {
        switch (error.type) {
            case ErrorType::DELETION:
                deletionCount++;
                errorPenalty += 0.05f;  // 删除错误的惩罚
                break;
                
            case ErrorType::SUBSTITUTION:
                substitutionCount++;
                errorPenalty += 0.03f;  // 替换错误的惩罚
                break;
                
            case ErrorType::DELETION_H_DROPPING:
            case ErrorType::DELETION_PLOSIVE_ELISION:
            case ErrorType::DELETION_SAME_CONSONANT:
                validLiaisonCount++;
                errorPenalty -= 0.01f;  // 合理的连读/省略奖励
                break;
                
            case ErrorType::SUBSTITUTION_Y_LINKING:
            case ErrorType::SUBSTITUTION_SH_LINKING:
            case ErrorType::SUBSTITUTION_NASALIZATION:
                validLiaisonCount++;
                errorPenalty -= 0.01f;  // 合理的连读奖励
                break;
                
            case ErrorType::SUBSTITUTION_WEAK:
                weakFormCount++;
                errorPenalty -= 0.02f;  // 弱读形式奖励
                break;
                
            default:
                break;
        }
    }
    
    // 限制错误惩罚的范围
    errorPenalty = std::max(-0.2f, std::min(0.2f, errorPenalty));
    
    // 计算最终得分
    float finalScore = baseScore - errorPenalty;
    
    // 确保得分在0-1范围内
    finalScore = std::max(0.0f, std::min(1.0f, finalScore));
    
    // 设置结果
    result.errors = errors;
    result.updatedScores = updatedScores;
    result.overallScore = finalScore;
}

void PhonemeAnalyzer::handleYLinking(
    PhonemeSpan& phoneSpan,
    PhonemeSpan& nextPhoneSpan,
    const std::string& targetPhoneme,
    const std::string& targetPhonemeNext,
    const std::vector<float>& logits,
    int vocabSize,
    std::vector<PhonemeError>& errors,
    int wordIdx,
    int phoneIdx) {
    
    // 检查当前音素是否在映射表中
    auto it = assimilationMap_.find(targetPhoneme);
    if (it != assimilationMap_.end()) {
        // 获取映射后的音素
        const std::string& mappedPhoneme = it->second;
        
        // 计算映射音素的概率（简化版，实际应该使用模型的logits）
        float mappedConfidence = CONFIDENCE_BOOST;
        
        // 更新分数
        phoneSpan.score += mappedConfidence;
        
        // 添加错误信息
        ErrorType errorType = (phoneSpan.score > 0.5f) ? 
                             ErrorType::SUBSTITUTION_Y_LINKING : 
                             ErrorType::SUBSTITUTION;
        
        errors.emplace_back(
            errorType,
            phoneIdx,
            targetPhoneme,
            targetPhoneme,  // 实际音素保持不变
            phoneSpan.score,
            phoneSpan.start,
            phoneSpan.end
        );
    }
    
    // 处理下一个音素是j的情况
    if (targetPhonemeNext == "j" && nextPhoneSpan.score <= SCORE_THRESHOLD) {
        // 限制j音素的分数不低于前一个音素的分数
        nextPhoneSpan.score = std::min(phoneSpan.score + 0.2f, MAX_SCORE);
        
        errors.emplace_back(
            ErrorType::SUBSTITUTION_Y_LINKING,
            phoneIdx + 1,
            targetPhonemeNext,
            "j",
            nextPhoneSpan.score,
            nextPhoneSpan.start,
            nextPhoneSpan.end
        );
    }
}

void PhonemeAnalyzer::handleSHLinking(
    PhonemeSpan& phoneSpan,
    const std::string& targetPhoneme,
    const std::vector<float>& logits,
    int vocabSize,
    std::vector<PhonemeError>& errors,
    int wordIdx,
    int phoneIdx,
    float timeStart,
    float timeEnd) {
    
    // 计算目标音素's'和替代音素'ʃ'的概率
    float sProb = phoneSpan.score;   
    float shProb = getPhonemeLogitScore("ʃ", phoneSpan.start, phoneSpan.end, logits, vocabSize);
    
    // 如果'ʃ'的概率加上's'的概率大于阈值（如0.5），则认为确实发生了SUBSTITUTION_SH_LINKING
    constexpr float SH_LINKING_THRESHOLD = 0.5f;
    if (shProb > 0.0f && (shProb + sProb) > SH_LINKING_THRESHOLD) {
        // 将音素得分设为两者概率之和
        phoneSpan.score = std::min(MAX_SCORE, shProb + sProb);
        
        // 记录错误信息
        errors.emplace_back(
            ErrorType::SUBSTITUTION_SH_LINKING,
            phoneIdx,
            targetPhoneme,
            "ʃ", // 使用替代音素作为actualPhoneme
            phoneSpan.score,
            timeStart,
            timeEnd
        );
    }
}

void PhonemeAnalyzer::handleNasalization(
    PhonemeSpan& phoneSpan,
    const std::string& targetPhoneme,
    const std::string& targetPhonemeNext,
    const std::vector<float>& logits,
    int vocabSize,
    std::vector<PhonemeError>& errors,
    int wordIdx,
    int phoneIdx,
    float timeStart,
    float timeEnd) {
    
    // 检查是否是鼻音化现象
    if (targetPhoneme == "n") {
        std::string nasalizedPhoneme;
        
        if (targetPhonemeNext == "p" || targetPhonemeNext == "b") {
            nasalizedPhoneme = "m";
        } else if (targetPhonemeNext == "k" || targetPhonemeNext == "g") {
            nasalizedPhoneme = "ŋ";
        }
        
        if (!nasalizedPhoneme.empty()) {
            // 计算目标音素和替代音素的概率
            float nProb = getPhonemeLogitScore(targetPhoneme, phoneSpan.start, phoneSpan.end, logits, vocabSize);
            float nasalProb = getPhonemeLogitScore(nasalizedPhoneme, phoneSpan.start, phoneSpan.end, logits, vocabSize);
            
            // 如果替代音素的概率较高，则认为发生了鼻音化
            constexpr float NASALIZATION_THRESHOLD = 0.5f;
            if (nasalProb > 0.0f && (nasalProb + nProb) > NASALIZATION_THRESHOLD) {
                // 将音素得分设为两者概率之和
                phoneSpan.score = std::min(MAX_SCORE, nasalProb + nProb);
                
                errors.emplace_back(
                    ErrorType::SUBSTITUTION_NASALIZATION,
                    phoneIdx,
                    targetPhoneme,
                    nasalizedPhoneme,
                    phoneSpan.score,
                    timeStart,
                    timeEnd
                );
            }
        }
    }
}

void PhonemeAnalyzer::handleWeakForms(
    PhonemeSpan& phoneSpan,
    const std::string& targetPhoneme,
    const std::string& word,
    const std::vector<float>& logits,
    int vocabSize,
    std::vector<PhonemeError>& errors,
    int wordIdx,
    int phoneIdx,
    float timeStart,
    float timeEnd) {
    
    // 处理your/you're的弱读
    if (targetPhoneme == "ʊɹ") {
        float erProb = getPhonemeLogitScore("ɚ", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        float orProb = getPhonemeLogitScore("ɔːɹ", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        
        std::string actualPhoneme = "ʊɹ";
        float highestProb = 0.0f;
        
        if (erProb > highestProb) {
            highestProb = erProb;
            actualPhoneme = "ɚ";
        }
        if (orProb > highestProb) {
            highestProb = orProb;
            actualPhoneme = "ɔːɹ";
        }
        
        if (actualPhoneme != "ʊɹ" && highestProb > 0.4f) {
            phoneSpan.score = std::min(MAX_SCORE, highestProb + 0.2f);
            
            errors.emplace_back(
                ErrorType::SUBSTITUTION_WEAK,
                phoneIdx,
                targetPhoneme,
                actualPhoneme,
                phoneSpan.score,
                timeStart,
                timeEnd
            );
            return;
        }
    }
    
    // 处理for的弱读
    if (word == "for" && (targetPhoneme == "ɔːɹ" || targetPhoneme == "ɚ")) {
        float orProb = getPhonemeLogitScore("ɔːɹ", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        float erProb = getPhonemeLogitScore("ɚ", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        float urProb = getPhonemeLogitScore("ɜː", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        
        std::string actualPhoneme = targetPhoneme;
        float highestProb = 0.0f;
        
        if (orProb > highestProb) {
            highestProb = orProb;
            actualPhoneme = "ɔːɹ";
        }
        if (erProb > highestProb) {
            highestProb = erProb;
            actualPhoneme = "ɚ";
        }
        if (urProb > highestProb) {
            highestProb = urProb;
            actualPhoneme = "ɜː";
        }
        
        if (actualPhoneme != targetPhoneme && highestProb > 0.4f) {
            phoneSpan.score = std::min(MAX_SCORE, highestProb + 0.2f);
            
            errors.emplace_back(
                ErrorType::SUBSTITUTION_WEAK,
                phoneIdx,
                targetPhoneme,
                actualPhoneme,
                phoneSpan.score,
                timeStart,
                timeEnd
            );
            return;
        }
    }
    // 检查是否是元音
    if (phoneme::PhoneSets::vowels().find(targetPhoneme) != phoneme::PhoneSets::vowels().end()) {
        // 计算目标音素概率
        float targetProb = getPhonemeLogitScore(targetPhoneme, phoneSpan.start, phoneSpan.end, logits, vocabSize);
        
        // 计算央元音概率（schwa）
        float schwaProb = getPhonemeLogitScore("ə", phoneSpan.start, phoneSpan.end, logits, vocabSize);
        
        // 如果目标是元音且schwa更强，视为弱读替换为schwa
        constexpr float SCHWA_REPLACE_THRESHOLD_SUM = 0.5f;
        if (targetPhoneme != "ə" && targetPhoneme != "ɐ" && schwaProb > targetProb && (targetProb + schwaProb) > SCHWA_REPLACE_THRESHOLD_SUM) {
            phoneSpan.score = std::min(MAX_SCORE, targetProb + schwaProb);
            errors.emplace_back(
                ErrorType::SUBSTITUTION_WEAK,
                phoneIdx,
                targetPhoneme,
                "ə",
                phoneSpan.score,
                timeStart,
                timeEnd
            );
            return;
        }
    }
}

} // namespace phoneme