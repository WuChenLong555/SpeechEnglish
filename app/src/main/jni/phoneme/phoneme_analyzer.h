#pragma once

#include <vector>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "phoneme_error.h"
#include "phoneme_scorer.h"
#include "liaison_rules.h"
#include "token_mapper.h"

namespace phoneme {

// 音素对齐项
struct PhonemeSpan {
    int token;
    int start;
    int end;
    float score;
    std::string text;
};

// 音素分析结果
struct PhonemeAnalysisResult {
    std::vector<PhonemeError> errors;
    std::vector<float> updatedScores;
    float overallScore;
};

// 音素分析器
class PhonemeAnalyzer {
public:
    PhonemeAnalyzer();
    ~PhonemeAnalyzer();

    // 初始化并加载token映射
//    bool initialize(AAssetManager* assetManager);

    // 分析音素并返回错误和优化后的得分
    PhonemeAnalysisResult analyzePhonemes(
        const std::vector<std::vector<PhonemeSpan>>& wordSpans,
        const std::vector<std::string>& words,
        const std::vector<float>& logits,
        int vocabSize
    );

    // 获取token映射器
    const TokenMapper& getTokenMapper() const { return tokenMapper_; }

private:
    // 连读规则
//    LiaisonRules liaisonRules_;
    
    // 音素映射表
    std::unordered_map<std::string, std::string> assimilationMap_;
    
    // Token映射器 - 硬编码实现
    TokenMapper tokenMapper_;
    
    // 初始化音素映射表
    void initAssimilationMap();
    
    // 处理Y连读
    void handleYLinking(
        PhonemeSpan& phoneSpan,
        PhonemeSpan& nextPhoneSpan,
        const std::string& targetPhoneme,
        const std::string& targetPhonemeNext,
        const std::vector<float>& logits,
        int vocabSize,
        std::vector<PhonemeError>& errors,
        int wordIdx,
        int phoneIdx
    );
    
    // 处理SH连读
    void handleSHLinking(
        PhonemeSpan& phoneSpan,
        const std::string& targetPhoneme,
        const std::vector<float>& logits,
        int vocabSize,
        std::vector<PhonemeError>& errors,
        int wordIdx,
        int phoneIdx,
        float timeStart,
        float timeEnd
    );
    
    // 从logits中获取特定音素的概率
    float getPhonemeLogitScore(
        const std::string& phoneme,
        int startFrame,
        int endFrame,
        const std::vector<float>& logits,
        int vocabSize
    );
    
    // 处理鼻音化
    void handleNasalization(
        PhonemeSpan& phoneSpan,
        const std::string& targetPhoneme,
        const std::string& targetPhonemeNext,
        const std::vector<float>& logits,
        int vocabSize,
        std::vector<PhonemeError>& errors,
        int wordIdx,
        int phoneIdx,
        float timeStart,
        float timeEnd
    );
    
    // 处理弱读形式
    void handleWeakForms(
        PhonemeSpan& phoneSpan,
        const std::string& targetPhoneme,
        const std::string& word,
        const std::vector<float>& logits,
        int vocabSize,
        std::vector<PhonemeError>& errors,
        int wordIdx,
        int phoneIdx,
        float timeStart,
        float timeEnd
    );
    
    // 计算优化后的评分
    void calculateOptimizedScore(
        const std::vector<float>& updatedScores,
        const std::vector<PhonemeError>& errors,
        PhonemeAnalysisResult& result
    );
    
    // 初始化标志
    bool initialized_ = false;

        void handleNasalization(PhonemeSpan &phoneSpan, const std::string &targetPhoneme,
                                const std::string &actualPhoneme,
                                const std::string &targetPhonemeNext,
                                const std::vector<float> &logits, int vocabSize,
                                std::vector<PhonemeError> &errors, int wordIdx, int phoneIdx,
                                float timeStart, float timeEnd);
    };

} // namespace phoneme