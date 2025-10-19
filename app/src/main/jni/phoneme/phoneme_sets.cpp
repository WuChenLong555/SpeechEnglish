#include "phoneme_sets.h"

namespace phoneme {

static const std::unordered_set<std::string>& make_set(std::initializer_list<const char*> list) {
    static std::unordered_set<std::string> dummy; // not used
    auto* s = new std::unordered_set<std::string>();
    s->reserve(list.size());
    for (auto* v : list) s->emplace(v);
    return *s;
}

const std::unordered_set<std::string>& PhoneSets::vowels() {
    static const auto& s = make_set({
        "i:", "i", "ɪ", "e", "æ", "ʌ", "ɜː", "ɜ",
        "u:", "u", "ʊ", "ɔː", "ɔ", "ɒ", "ɑː", "ɑ", "ɐ", "ɛ"
    });
    return s;
}

const std::unordered_set<std::string>& PhoneSets::diphthongs() {
    static const auto& s = make_set({
        "eɪ", "oʊ", "aɪ", "ɔɪ", "əʊ", "aʊ", "ɪə", "eə", "ʊə", "ɪɹ", "ɑːɹ"
    });
    return s;
}

const std::unordered_set<std::string>& PhoneSets::rhoticVowels() {
    static const auto& s = make_set({
        "ɪɹ", "ɛɹ", "ʊɹ", "ɔːɹ", "ɑːɹ", "ɚ", "ɜ", "ɜː"
    });
    return s;
}

const std::unordered_set<std::string>& PhoneSets::allVowels() {
    static auto* s = new std::unordered_set<std::string>();
    if (s->empty()) {
        s->insert(vowels().begin(), vowels().end());
        s->insert(diphthongs().begin(), diphthongs().end());
        s->insert(rhoticVowels().begin(), rhoticVowels().end());
    }
    return *s;
}

const std::unordered_set<std::string>& PhoneSets::plosives() {
    static const auto& s = make_set({"p","b","t","d","k","ɡ","ɾ"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::fricatives() {
    static const auto& s = make_set({"f","v","θ","ð","s","z","ʃ","ʒ","h"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::affricates() {
    static const auto& s = make_set({"tʃ","dʒ"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::nasals() {
    static const auto& s = make_set({"m","n","ŋ"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::liquids() {
    static const auto& s = make_set({"l","r","ɹ"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::glides() {
    static const auto& s = make_set({"j","w"});
    return s;
}

const std::unordered_set<std::string>& PhoneSets::consonants() {
    static auto* s = new std::unordered_set<std::string>();
    if (s->empty()) {
        s->insert(plosives().begin(), plosives().end());
        s->insert(fricatives().begin(), fricatives().end());
        s->insert(affricates().begin(), affricates().end());
        s->insert(nasals().begin(), nasals().end());
        s->insert(liquids().begin(), liquids().end());
        s->insert(glides().begin(), glides().end());
    }
    return *s;
}

const std::unordered_set<std::string>& PhoneSets::functionWords() {
    static const auto& s = make_set({
        "in","on","at","to","of","from","as",
        "will","shall","must","can","am","is","are","was","were","don't","have","has",
        "them",
        "and","or","but","yet"
    });
    return s;
}

} // namespace phoneme


