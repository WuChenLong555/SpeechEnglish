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

    // h脱落：需要目标词在集合中，且位置非initial/pause，且前一个音素为辅音
    public static boolean checkHDropping(String word, String prevPhoneme) {
        if (word == null || prevPhoneme == null) return false;
        if (!PronounChecker.isHPronoun(word)) return false;
        return isConsonant(prevPhoneme);
    }

    /**
     * 检查两个音素之间的连读类型
     * @param phoneme1 第一个音素
     * @param phoneme2 第二个音素
     * @param word2 第二个单词（用于h音脱落判断）
     * @return 连读类型，如果没有连读则返回null
     */
    public static LiaisonType checkLiaison(String phoneme1, String phoneme2,
                                           String word2) {
        // j 连音：高前元音或相应双元音后接任意元音
        if (("i:".equals(phoneme1) || "ɪ".equals(phoneme1) || "eɪ".equals(phoneme1)
                || "aɪ".equals(phoneme1) || "ɔɪ".equals(phoneme1))
                && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.J_LINKING;
        }

        // w 连音：圆唇元音或相应双元音后接任意元音
        if (("u:".equals(phoneme1) || "oʊ".equals(phoneme1) || "aʊ".equals(phoneme1))
                && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.W_LINKING;
        }

        // r 连音：r/ɹ/儿化元音后接任意元音
        if (("r".equals(phoneme1) || "ɹ".equals(phoneme1)
                || "ɪɹ".equals(phoneme1) || "ɚ".equals(phoneme1)
                || "ɜ".equals(phoneme1) || "ɜː".equals(phoneme1))
                && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.R_LINKING;
        }

        // 鼻音 + 元音
        if (IPAPhoneSets.NASALS.contains(phoneme1) && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.NASAL_VOWEL;
        }
        // 摩擦音 + 元音
        if (IPAPhoneSets.FRICATIVES.contains(phoneme1) && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.FRICATIVE_VOWEL;
        }
        // 爆破音 + 元音
        if (IPAPhoneSets.PLOSIVES.contains(phoneme1) && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.PLOSIVE_VOWEL;
        }

        // 辅音 + 元音
        if (isConsonant(phoneme1) && IPAPhoneSets.ALL_VOWELS.contains(phoneme2)) {
            return LiaisonType.CONSONANT_VOWEL;
        }

        // SH 连读
        if (("s".equals(phoneme1) || "z".equals(phoneme1) || "ʃ".equals(phoneme1)) && "ʃ".equals(phoneme2)) {
            return LiaisonType.SH_LIAISON;
        }

        // Y 连读（半元音）
        if (("s".equals(phoneme1) || "z".equals(phoneme1) || "t".equals(phoneme1) || "d".equals(phoneme1))
                && "j".equals(phoneme2)) {
            return LiaisonType.Y_LIAISON;
        }

        // h 脱落
        if ("h".equals(phoneme2)) {
            if (word2 != null && checkHDropping(word2, phoneme1)) {
                return LiaisonType.H_DROPPING;
            }
            return null;
        }

        // 相同辅音（排除爆破音）
        if (isConsonant(phoneme1) && phoneme1.equals(phoneme2) && !IPAPhoneSets.PLOSIVES.contains(phoneme1)) {
            return LiaisonType.SAME_CONSONANT;
        }

        // 爆破音省略：后续为任意辅音
        if (IPAPhoneSets.PLOSIVES.contains(phoneme1) && isConsonant(phoneme2)) {
            return LiaisonType.PLOSIVE_ELISION;
        }

        // 鼻化：鼻音后接部分爆破音
        if (IPAPhoneSets.NASALS.contains(phoneme1) && ("k".equals(phoneme2) || "ɡ".equals(phoneme2)
                || "p".equals(phoneme2) || "b".equals(phoneme2))) {
            return LiaisonType.NASALIZATION;
        }

        return null;
    }
} 