package com.speech.english.liaison;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 只针对 he, his, him, her 的代词检查
 */
public class PronounChecker {
    private static final Set<String> H_PRONOUNS = new HashSet<>(Arrays.asList(
        "he", "his", "him", "her"
    ));

    public static boolean isHPronoun(String word) {
        if (word == null) return false;
        return H_PRONOUNS.contains(word.toLowerCase());
    }
} 