#include "liaison_rules.h"

namespace phoneme {

LiaisonKind checkLiaison(const std::string& p1,
                         const std::string& p2,
                         const std::string& word2,
                         const std::string& position) {
    // J linking
    if ((p1 == "i:" || p1 == "ɪ" || p1 == "eɪ" || p1 == "aɪ" || p1 == "ɔɪ") && isVowel(p2))
        return LiaisonKind::J_LINKING;

    // W linking
    if ((p1 == "u:" || p1 == "oʊ" || p1 == "aʊ") && isVowel(p2))
        return LiaisonKind::W_LINKING;

    // R linking
    if ((p1 == "r" || p1 == "ɹ" || p1 == "ɪɹ" || p1 == "ɚ" || p1 == "ɜ" || p1 == "ɜː") && isVowel(p2))
        return LiaisonKind::R_LINKING;

    // Nasal + vowel
    if (PhoneSets::nasals().count(p1) && isVowel(p2)) return LiaisonKind::NASAL_VOWEL;
    // Fricative + vowel
    if (PhoneSets::fricatives().count(p1) && isVowel(p2)) return LiaisonKind::FRICATIVE_VOWEL;
    // Plosive + vowel
    if (PhoneSets::plosives().count(p1) && isVowel(p2)) return LiaisonKind::PLOSIVE_VOWEL;

    // Consonant + vowel
    if (isConsonant(p1) && isVowel(p2)) return LiaisonKind::CONSONANT_VOWEL;

    // SH linking
    if ((p1 == "s" || p1 == "z" || p1 == "ʃ") && p2 == "ʃ") return LiaisonKind::SH_LINKING;

    // Y linking
    if ((p1 == "s" || p1 == "z" || p1 == "t" || p1 == "d") && p2 == "j") return LiaisonKind::Y_LINKING;

    // h dropping
    if (p2 == "h") {
        if (checkHDropping(word2, p1, position)) return LiaisonKind::H_DROPPING;
        return LiaisonKind::NONE;
    }

    // Same consonant (exclude plosives)
    if (isConsonant(p1) && p1 == p2 && !PhoneSets::plosives().count(p1))
        return LiaisonKind::SAME_CONSONANT;

    // Plosive elision (followed by any consonant)
    if (PhoneSets::plosives().count(p1) && isConsonant(p2))
        return LiaisonKind::PLOSIVE_ELISION;

    // Nasalization
    if (PhoneSets::nasals().count(p1) && (p2 == "k" || p2 == "ɡ" || p2 == "p" || p2 == "b"))
        return LiaisonKind::NASALIZATION;

    return LiaisonKind::NONE;
}

} // namespace phoneme


