package com.speech.english.liaison;

import java.util.HashSet;
import java.util.Set;

/**
 * IPA音素集合定义
 */
public class IPAPhoneSets {
    // 元音'ɐ'，弱读的e  补充on的元音'ɔ'；补充美式常见元音 i, u, ɛ
    public static final Set<String> VOWELS = new HashSet<String>() {{
        add("i:");
        add("i");
        add("ɪ");
        add("e");
        add("æ");
        add("ʌ");
        add("ɜː");
        add("ɜ");
        add("ə");
        add("u:");
        add("u");
        add("ʊ");
        add("ɔː");
        add("ɔ");
        add("ɒ");
        add("ɑː");
        add("ɑ");
        add("ɐ");
        add("ɛ");
    }};

    // 双元音：补充美式 oʊ；保留 ɪɹ 与 ɑːɹ 以兼容模型输出
    public static final Set<String> DIPHTHONGS = new HashSet<String>() {{
        add("eɪ");
        add("oʊ");
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

    // 儿化元音/卷舌元音（美式）
    public static final Set<String> RHOTIC_VOWELS = new HashSet<String>() {{
        add("ɪɹ");
        add("ɛɹ");
        add("ʊɹ");
        add("ɔːɹ");
        add("ɑːɹ");
        add("ɚ");
        add("ɜ");
        add("ɜː");
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

    // 所有元音（包括单元音、双元音与儿化元音）
    public static final Set<String> ALL_VOWELS = new HashSet<String>() {{
        addAll(VOWELS);
        addAll(DIPHTHONGS);
        addAll(RHOTIC_VOWELS);
    }};

    // 功能词集合（介词/助动词/代词/连词）用于弱读等策略
    public static final Set<String> PREPOSITIONS = new HashSet<String>() {{
        add("in"); add("on"); add("at"); add("to"); add("of"); add("from"); add("as");
    }};
    public static final Set<String> AUXILIARIES = new HashSet<String>() {{
        add("will"); add("shall"); add("must"); add("can"); add("am"); add("is"); add("are");
        add("was"); add("were"); add("don't"); add("have"); add("has");
    }};
    public static final Set<String> PRONOUNS = new HashSet<String>() {{
        add("them");
    }};
    public static final Set<String> CONJUNCTIONS = new HashSet<String>() {{
        add("and"); add("or"); add("but"); add("yet");
    }};
    public static final Set<String> FUNCTION_WORDS = new HashSet<String>() {{
        addAll(PREPOSITIONS);
        addAll(AUXILIARIES);
        addAll(PRONOUNS);
        addAll(CONJUNCTIONS);
    }};
} 