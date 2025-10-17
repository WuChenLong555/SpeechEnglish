package com.speech.english.liaison;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * h-开头弱化代词及相关词的检查
 */
public class PronounChecker {
    private static final Set<String> H_PRONOUNS = new HashSet<>(Arrays.asList(
        // 参考 Python 中的集合，适度扩展并统一为小写
        "he", "his", "him", "her", "here", "house",
        // 带标点的常见形式（来自参考）
        "him,", "her,", "he,", "here,"
    ));

    public static boolean isHPronoun(String word) {
        if (word == null) return false;
        return H_PRONOUNS.contains(word.toLowerCase());
    }
} 