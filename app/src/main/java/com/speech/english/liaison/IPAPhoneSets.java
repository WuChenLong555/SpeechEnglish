package com.speech.english.liaison;

import java.util.HashSet;
import java.util.Set;

/**
 * IPA音素集合定义
 */
public class IPAPhoneSets {
    // 元音'ɐ'，弱读的e  补充on的元音'ɔ'
    public static final Set<String> VOWELS = new HashSet<String>() {{
        add("i:");
        add("ɪ");
        add("e");
        add("æ");
        add("ʌ");
        add("ɜː");
        add("ə");
        add("u:");
        add("ʊ");
        add("ɔː");
        add("ɒ");
        add("ɑː");
        add("ɐ");
        add("ɔ");
    }};

    // 双元音 为了适应模型添加的双元音'ɑːɹ'
    public static final Set<String> DIPHTHONGS = new HashSet<String>() {{
        add("eɪ");
        add("aɪ");
        add("ɔɪ");
        add("əʊ");
        add("aʊ");
        add("ɪə");
        add("eə");
        add("ʊə");
        add("ɪɹ");
        add("ɑːɹ");
    }};

    // 辅音
    public static final Set<String> PLOSIVES = new HashSet<String>() {{  // 爆破音
        add("p");
        add("b");
        add("t");
        add("d");
        add("k");
        add("ɡ");
        add("ɾ");
    }};

    public static final Set<String> FRICATIVES = new HashSet<String>() {{  // 摩擦音
        add("f");
        add("v");
        add("θ");
        add("ð");
        add("s");
        add("z");
        add("ʃ");
        add("ʒ");
        add("h");
    }};

    public static final Set<String> AFFRICATES = new HashSet<String>() {{  // 破擦音
        add("tʃ");
        add("dʒ");
    }};

    public static final Set<String> NASALS = new HashSet<String>() {{  // 鼻音
        add("m");
        add("n");
        add("ŋ");
    }};

    public static final Set<String> LIQUIDS = new HashSet<String>() {{  // 流音
        add("l");
        add("r");
        add("ɹ");
    }};

    public static final Set<String> GLIDES = new HashSet<String>() {{  // 滑音
        add("j");
        add("w");
    }};

    // 所有辅音
    public static final Set<String> CONSONANTS = new HashSet<String>() {{
        addAll(PLOSIVES);
        addAll(FRICATIVES);
        addAll(AFFRICATES);
        addAll(NASALS);
        addAll(LIQUIDS);
        addAll(GLIDES);
    }};

    // 所有元音（包括单元音和双元音）
    public static final Set<String> ALL_VOWELS = new HashSet<String>() {{
        addAll(VOWELS);
        addAll(DIPHTHONGS);
    }};
} 