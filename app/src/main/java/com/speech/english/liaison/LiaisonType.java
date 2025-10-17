package com.speech.english.liaison;

/**
 * 连读类型枚举
 */
public enum LiaisonType {
    VOWEL_VOWEL("VOWEL_VOWEL"),           // 元音+元音连读
    CONSONANT_VOWEL("CONSONANT_VOWEL"),   // 辅音+元音连读
    PLOSIVE_ELISION("PLOSIVE_ELISION"),   // 爆破音省略
    H_DROPPING("H_DROPPING"),             // h音脱落
    R_LINKING("R_LINKING"),               // r连音
    J_LINKING("J_LINKING"),               // j连音（在高前元音后）
    W_LINKING("W_LINKING"),               // w连音（在圆唇元音后）
    SAME_CONSONANT("SAME_CONSONANT"),     // 相同辅音连读
    SH_LIAISON("SH_LINKING"),            // SH连读
    Y_LIAISON("Y_LINKING"),              // Y连读
    LIQUID_VOWEL("LIQUID_VOWEL"),        // 流音+元音
    NASAL_VOWEL("NASAL_VOWEL"),          // 鼻音+元音
    FRICATIVE_VOWEL("FRICATIVE_VOWEL"),  // 摩擦音+元音
    PLOSIVE_VOWEL("PLOSIVE_VOWEL"),      // 爆破音+元音
    NASALIZATION("NASALIZATION"),        // 鼻化
    SCHWA("SCHWA");                      // 中元音弱化

    private final String value;

    LiaisonType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
} 