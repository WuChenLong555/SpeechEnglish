#pragma once

#include <string>
#include <vector>
#include <optional>
#include "liaison_rules.h"

namespace phoneme {

// 音素错误类型
enum class ErrorType {
    NONE,                   // 无错误
    DELETION,               // 删除错误
    DELETION_ELISION,       // 爆破音省略删除
    SUBSTITUTION,           // 替换错误
    SUBSTITUTION_SH_LINKING,// SH连读替换
    SUBSTITUTION_Y_LINKING, // Y连读替换
    SUBSTITUTION_NASALIZATION, // 鼻音化替换
    SUBSTITUTION_WEAK,      // 弱读替换
    SUBSTITUTION_NONWEAK,   // 非弱读替换
    INSERTION,               // 插入错误
    DELETION_SAME_CONSONANT, //相同辅音删除
    DELETION_H_DROPPING,      //H音删除
    DELETION_PLOSIVE_ELISION, //爆破音省略


};

// 音素错误信息
struct PhonemeError {
    ErrorType type;
    int position;
    std::string expectedPhoneme;
    std::string actualPhoneme;  // 可能为空
    float confidence;
    float timeStart;
    float timeEnd;
    
    // 构造函数
    PhonemeError(
        ErrorType type,
        int position,
        const std::string& expectedPhoneme,
        const std::string& actualPhoneme,
        float confidence,
        float timeStart,
        float timeEnd
    ) : type(type),
        position(position),
        expectedPhoneme(expectedPhoneme),
        actualPhoneme(actualPhoneme),
        confidence(confidence),
        timeStart(timeStart),
        timeEnd(timeEnd) {}
        
    // 默认构造函数
    PhonemeError() : 
        type(ErrorType::NONE),
        position(0),
        expectedPhoneme(""),
        actualPhoneme(""),
        confidence(0.0f),
        timeStart(0.0f),
        timeEnd(0.0f) {}
};

// 将字符串转换为错误类型
inline ErrorType stringToErrorType(const std::string& typeStr) {
    if (typeStr == "deletion") return ErrorType::DELETION;
    if (typeStr == "deletion_elision") return ErrorType::DELETION_ELISION;
    if (typeStr == "substitution") return ErrorType::SUBSTITUTION;
    if (typeStr == "substitution_SH_LINKING") return ErrorType::SUBSTITUTION_SH_LINKING;
    if (typeStr == "substitution_Y_LINKING") return ErrorType::SUBSTITUTION_Y_LINKING;
    if (typeStr == "substitution_NASALIZATION") return ErrorType::SUBSTITUTION_NASALIZATION;
    if (typeStr == "substitution_weak") return ErrorType::SUBSTITUTION_WEAK;
    if (typeStr == "substitution_NonWeak") return ErrorType::SUBSTITUTION_NONWEAK;
    if (typeStr == "insertion") return ErrorType::INSERTION;
    return ErrorType::NONE;
}

// 将错误类型转换为字符串
inline std::string errorTypeToString(ErrorType type) {
    switch (type) {
        case ErrorType::DELETION: return "deletion";
        case ErrorType::DELETION_ELISION: return "deletion_elision";
        case ErrorType::SUBSTITUTION: return "substitution";
        case ErrorType::SUBSTITUTION_SH_LINKING: return "substitution_SH_LINKING";
        case ErrorType::SUBSTITUTION_Y_LINKING: return "substitution_Y_LINKING";
        case ErrorType::SUBSTITUTION_NASALIZATION: return "substitution_NASALIZATION";
        case ErrorType::SUBSTITUTION_WEAK: return "substitution_weak";
        case ErrorType::SUBSTITUTION_NONWEAK: return "substitution_NonWeak";
        case ErrorType::INSERTION: return "insertion";
        default: return "none";
    }
}

} // namespace phoneme