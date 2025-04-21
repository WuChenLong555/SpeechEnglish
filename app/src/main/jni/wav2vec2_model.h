#ifndef WAV2VEC2_MODEL_H
#define WAV2VEC2_MODEL_H

namespace wav2vec2 {
    // 输入输出层名称 - 根据param文件内容更新
    static const char* INPUT_LAYER = "in0";       // 对应param文件第3行的Input层
    static const char* OUTPUT_LAYER = "out0";     // 对应param文件最后一行的输出层

    // 模型参数
    struct ModelConfig {
        static const int SAMPLE_RATE = 16000;      // 采样率
        static const int FEATURE_DIM = 512;        // 特征维度
        static const int HIDDEN_DIM = 1024;        // 隐藏层维度
        static const int NUM_LAYERS = 12;          // Transformer层数
        static const int NUM_HEADS = 16;           // 注意力头数
        static const int HEAD_DIM = 64;            // 每个头的维度
        static const int INPUT_SIZE = 400;         // 输入帧大小 (25ms * 16000Hz)
        static const int HOP_LENGTH = 320;         // 步长 (20ms * 16000Hz)，修正为论文中的值
        static const int CONTEXT_SIZE = 150;       // 上下文大小，从para文件中可见
    };

    // 关键层名称
    namespace layers {
        // 特征提取层
        static const char* FEATURE_EXTRACTOR_CONV1 = "conv1d_148";
        static const char* FEATURE_EXTRACTOR_CONV2 = "conv1d_149";
        static const char* FEATURE_EXTRACTOR_CONV3 = "conv1d_150";
        static const char* FEATURE_EXTRACTOR_CONV4 = "conv1d_151";
        static const char* FEATURE_EXTRACTOR_CONV5 = "conv1d_152";
        static const char* FEATURE_EXTRACTOR_CONV6 = "conv1d_153";
        static const char* FEATURE_EXTRACTOR_CONV7 = "conv1d_154";

        // Transformer层
        static const char* TRANSFORMER_PREFIX = "F.scaled_dot_product_attention_";
        static const char* LAYER_NORM_PREFIX = "ln_";
        static const char* ATTENTION_PREFIX = "gemm_";
    };

    // 模型输出处理
    struct OutputConfig {
        static const int NUM_TOKENS = 392;  // 音素词表大小，从param文件可知输出大小为392
        static constexpr float THRESHOLD = 0.5f;  // 输出阈值，使用constexpr
    };

    // 错误码
    enum ErrorCode {
        SUCCESS = 0,
        ERROR_INIT_FAILED = -1,
        ERROR_AUDIO_LENGTH = -2,
        ERROR_PROCESS_FAILED = -3,
        ERROR_NULL_POINTER = -4
    };
}

#endif // WAV2VEC2_MODEL_H 