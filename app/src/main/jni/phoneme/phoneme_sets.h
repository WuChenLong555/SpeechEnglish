#pragma once

#include <unordered_set>
#include <string>

namespace phoneme {

// 音素集合：与 Java 侧 IPAPhoneSets 对齐（精简不可变集合）
struct PhoneSets {
    static const std::unordered_set<std::string>& vowels();
    static const std::unordered_set<std::string>& diphthongs();
    static const std::unordered_set<std::string>& rhoticVowels();
    static const std::unordered_set<std::string>& allVowels();

    static const std::unordered_set<std::string>& plosives();
    static const std::unordered_set<std::string>& fricatives();
    static const std::unordered_set<std::string>& affricates();
    static const std::unordered_set<std::string>& nasals();
    static const std::unordered_set<std::string>& liquids();
    static const std::unordered_set<std::string>& glides();
    static const std::unordered_set<std::string>& consonants();

    static const std::unordered_set<std::string>& functionWords();
};

inline bool isConsonant(const std::string& p) {
    return PhoneSets::consonants().count(p) > 0;
}

inline bool isVowel(const std::string& p) {
    return PhoneSets::allVowels().count(p) > 0;
}

} // namespace phoneme


