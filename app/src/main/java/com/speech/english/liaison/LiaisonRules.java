package com.speech.english.liaison;

/**
 * 连读规则处理类
 */
public class LiaisonRules {
    
    /**
     * 判断是否为辅音
     */
    public static boolean isConsonant(String phoneme) {
        return IPAPhoneSets.CONSONANTS.contains(phoneme);
    }

    /**
     * 检查h音是否应该脱落
     * 只针对 he, his, him, her，且仅在句中或句尾（middle, final）才脱落
     * @param word 包含h的单词
     * @param position 'initial'(句首), 'middle'(句中), 'final'(句尾), 'pause'(停顿后)
     * @return 是否应该脱落h音
     */
    public static boolean checkHDropping(String word, String position) {
        if (!PronounChecker.isHPronoun(word)) {
            return false;
        }
        // 句首或停顿后不脱落
        if ("initial".equals(position) || "pause".equals(position)) {
            return false;
        }
        // 句中或句尾可以脱落
        return "middle".equals(position) || "final".equals(position);
    }

    /**
     * 检查两个音素之间的连读类型
     * @param phoneme1 第一个音素
     * @param phoneme2 第二个音素
     * @param word2 第二个单词（用于h音脱落判断）
     * @param position 位置信息
     * @return 连读类型，如果没有连读则返回null
     */
    public static LiaisonType checkLiaison(String phoneme1, String phoneme2, 
                                         String word2, String position) {
        // 元音 + 元音连读
        if (IPAPhoneSets.ALL_VOWELS.contains(phoneme1) && 
            IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.VOWEL_VOWEL;
        }
        
        // j连音（在高前元音后）
        if (("i:".equals(phoneme1) || "ɪ".equals(phoneme1)) && 
            IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.J_LINKING;
        }
        
        // w连音（在圆唇元音后）
        if (("u:".equals(phoneme1) || "ʊ".equals(phoneme1)) && 
            IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.W_LINKING;
        }
        
        // 辅音 + 元音连读
        if (isConsonant(phoneme1) && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.CONSONANT_VOWEL;
        }
        
        // SH连读
        if (("s".equals(phoneme1) || "z".equals(phoneme1)) && "ʃ".equals(phoneme2)) {
            return LiaisonType.SH_LIAISON;
        }
        
        // Y连读（半元音连读）
        if (("s".equals(phoneme1) || "z".equals(phoneme1) || 
             "t".equals(phoneme1) || "d".equals(phoneme1) || 
             "k".equals(phoneme1)) && "j".equals(phoneme2)) {
            return LiaisonType.Y_LIAISON;
        }
        
        // h音脱落
        if ("h".equals(phoneme2)) {
            if (word2 != null && checkHDropping(word2, position)) {
                return LiaisonType.H_DROPPING;
            }
            return null;
        }
        
        // r连音
        if (("r".equals(phoneme1) || "ɹ".equals(phoneme1) || "ɪɹ".equals(phoneme1)) && 
            IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.R_LINKING;
        }
        
        // 相同辅音连读
        if (isConsonant(phoneme1) && phoneme1.equals(phoneme2)) {
            return LiaisonType.SAME_CONSONANT;
        }
        
        // 爆破音省略（当后面跟辅音时）
        if (IPAPhoneSets.PLOSIVES.contains(phoneme1) && isConsonant(phoneme2)) {
            return LiaisonType.PLOSIVE_ELISION;
        }
        
        return null;
    }
} 