#include "token_mapper.h"
#include <android/log.h>

#define LOG_TAG "TokenMapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

TokenMapper::TokenMapper() : initialized_(false) {
}

TokenMapper::~TokenMapper() {
}

void TokenMapper::initialize() {
    // 清空现有映射
    id_to_token_.clear();
    token_to_id_.clear();

    // 使用Wav2Vec2_Phone_to_id映射关系
    const std::vector<std::pair<int, std::string>> tokens = {
        {0, "pad"},
        {41, "ɑː"},
        {42, "ŋ"},
        {36, "æ"},
        {30, "iː"},
        {24, "j"},
        {104, "ʊɹ"},
        {33, "ʌ"},
        {12, "d"},
        {66, "tʃ"},
        {11, "k"},
        {53, "aʊ"},
        {169, "n̩"},
        {65, "ʒ"},
        {13, "m"},
        {32, "w"},
        {97, "ɪɹ"},
        {6, "t"},
        {37, "aɪ"},
        {5, "s"},
        {14, "ɛ"},
        {81, "iə"},
        {100, "ɔɪ"},
        {50, "ᵻ"},
        {4, "n"},
        {20, "ɐ"},
        {39, "h"},
        {35, "ɡ"},
        {43, "ɚ"},
        {23, "f"},
        {29, "ʊ"},
        {94, "ɛɹ"},
        {60, "dʒ"},
        {61, "əl"},
        {10, "i"},
        {17, "ɪ"},
        {68, "ɑːɹ"},
        {139, "aɪə"},
        {7, "ə"},
        {67, "ɔː"},
        {71, "ɔːɹ"},
        {131, "aɪɚ"},
        {27, "ɹ"},
        {49, "oʊ"},
        {22, "ð"},
        {18, "p"},
        {40, "ɔ"},
        {52, "θ"},
        {44, "eɪ"},
        {26, "b"},
        {15, "ɾ"},
        {21, "z"},
        {8, "l"},
        {46, "uː"},
        {80, "ʔ"},
        {63, "ɜː"},
        {38, "ʃ"},
        {25, "v"},
        // 添加一些特殊token
        {1, "<s>"},
        {2, "</s>"},
        {3, "<unk>"}
    };

    // 添加到映射中
    for (const auto& pair : tokens) {
        id_to_token_[pair.first] = pair.second;
        token_to_id_[pair.second] = pair.first;
    }

    initialized_ = true;
    LOGI("Initialized %zu tokens", id_to_token_.size());
}

std::string TokenMapper::getTokenById(int id) const {
    auto it = id_to_token_.find(id);
    if (it != id_to_token_.end()) {
        return it->second;
    }
    return "";  // 未找到返回空字符串
}

int TokenMapper::getIdByToken(const std::string& token) const {
    auto it = token_to_id_.find(token);
    if (it != token_to_id_.end()) {
        return it->second;
    }
    return -1;  // 未找到返回-1
}