#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include <android/log.h>
#include "net.h"
#include <vector>
#include <cmath>
#include "wav2vec2_model.h"

#define TAG "Wav2Vec2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

class ScaledDotProductAttention : public ncnn::Layer
{
public:
    ScaledDotProductAttention()
    {
        one_blob_only = false;
        support_inplace = false;
    }

    virtual int forward(const std::vector<ncnn::Mat>& bottom_blobs, std::vector<ncnn::Mat>& top_blobs, const ncnn::Option& opt) const
    {
        const ncnn::Mat& q = bottom_blobs[0];
        const ncnn::Mat& k = bottom_blobs[1];
        const ncnn::Mat& v = bottom_blobs[2];

        int batch = q.c;
        int num_heads = q.h;
        int seq_len = q.w;
        int head_dim = q.elemsize;

        float scale = 1.0f / sqrt(head_dim);

        ncnn::Mat& out = top_blobs[0];
        out.create(seq_len, num_heads, batch);
        if (out.empty())
        {
            LOGE("Failed to create output blob");
            return -1;
        }

        #pragma omp parallel for
        for (int b = 0; b < batch; b++)
        {
            for (int h = 0; h < num_heads; h++)
            {
                // 使用Mat的data指针直接访问数据
                float* q_ptr = (float*)q.channel(b).row(h);
                float* k_ptr = (float*)k.channel(b).row(h);
                float* v_ptr = (float*)v.channel(b).row(h);
                float* out_ptr = (float*)out.channel(b).row(h);

                // 创建临时scores矩阵
                ncnn::Mat scores(seq_len, seq_len);
                if (scores.empty())
                {
                    LOGE("Failed to create scores matrix");
                    continue;
                }
                float* scores_ptr = (float*)scores.data;

                // 计算Q*K^T
                for (int i = 0; i < seq_len; i++)
                {
                    for (int j = 0; j < seq_len; j++)
                    {
                        float sum = 0.f;
                        for (int d = 0; d < head_dim; d++)
                        {
                            sum += q_ptr[i * head_dim + d] * k_ptr[j * head_dim + d];
                        }
                        scores_ptr[i * seq_len + j] = sum * scale;
                    }
                }

                // Softmax
                for (int i = 0; i < seq_len; i++)
                {
                    float max_val = -FLT_MAX;
                    for (int j = 0; j < seq_len; j++)
                    {
                        max_val = std::max(max_val, scores_ptr[i * seq_len + j]);
                    }

                    float sum = 0.f;
                    for (int j = 0; j < seq_len; j++)
                    {
                        scores_ptr[i * seq_len + j] = exp(scores_ptr[i * seq_len + j] - max_val);
                        sum += scores_ptr[i * seq_len + j];
                    }

                    for (int j = 0; j < seq_len; j++)
                    {
                        scores_ptr[i * seq_len + j] /= sum;
                    }
                }

                // 计算attention结果
                for (int i = 0; i < seq_len; i++)
                {
                    for (int d = 0; d < head_dim; d++)
                    {
                        float sum = 0.f;
                        for (int j = 0; j < seq_len; j++)
                        {
                            sum += scores_ptr[i * seq_len + j] * v_ptr[j * head_dim + d];
                        }
                        out_ptr[i * head_dim + d] = sum;
                    }
                }
            }
        }

        return 0;
    }
};

DEFINE_LAYER_CREATOR(ScaledDotProductAttention)

class Wav2Vec2 {
private:
    ncnn::Net net;
    bool initialized = false;

    // 音频预处理参数
    static constexpr int SAMPLE_RATE = wav2vec2::ModelConfig::SAMPLE_RATE;
    static constexpr int FRAME_LENGTH = 400;  // 25ms at 16kHz
    static constexpr int FRAME_SHIFT = 320;   // 20ms at 16kHz
    static constexpr int FEATURE_DIM = wav2vec2::ModelConfig::FEATURE_DIM;

    // 音频预处理函数
    std::vector<float> preprocessAudio(const float* audioData, int length) {
        if (!audioData || length <= 0) {
            LOGE("Invalid audio data or length");
            return std::vector<float>();
        }

        LOGI("Starting audio preprocessing: length=%d samples", length);

        // 1. 归一化音频数据到[-1, 1]范围
        std::vector<float> normalized(length);
        float max_val = 0.0f;
        
        // 找出最大绝对值
        for (int i = 0; i < length; i++) {
            max_val = std::max(max_val, std::abs(audioData[i]));
        }
        
        LOGI("Audio normalization: max amplitude=%f", max_val);
        
        // 执行归一化
        if (max_val > 0.0001f) {
            for (int i = 0; i < length; i++) {
                normalized[i] = audioData[i] / max_val;
            }
            LOGI("Audio normalized to range [-1, 1]");
        } else {
            LOGE("Audio is silent (max_val too small), using original data");
            normalized.assign(audioData, audioData + length);
        }

        // 检查音频长度是否足够Wav2Vec2处理
        // Wav2Vec2通常需要至少400个样本（25ms@16kHz）才能产生有意义的特征
        int min_length = std::max(400, FRAME_LENGTH);
        
        // 如果音频太短，进行零填充
        if (length < min_length) {
            LOGI("Audio too short (%d samples), padding to %d samples", length, min_length);
            std::vector<float> padded(min_length, 0.0f);
            std::copy(normalized.begin(), normalized.end(), padded.begin());
            return padded;
        }
        
        LOGI("Audio processing complete: %d samples", normalized.size());
        return normalized;
    }

    // 检查asset文件是否存在
    bool assetExists(AAssetManager* mgr, const char* filename) {
        if (!mgr) return false;
        
        AAsset* asset = AAssetManager_open(mgr, filename, AASSET_MODE_BUFFER);
        if (!asset) {
            LOGE("Asset file not found: %s", filename);
            return false;
        }
        
        LOGI("Asset file exists: %s, size: %ld bytes", filename, AAsset_getLength(asset));
        AAsset_close(asset);
        return true;
    }

public:
    Wav2Vec2() {
        // 不在构造函数中注册自定义层，而是在init方法中进行
    }

    bool init(AAssetManager* mgr) {
        if (initialized) {
            LOGI("Already initialized, skipping initialization");
            return true;
        }

        if (!mgr) {
            LOGE("Asset manager is null");
            return false;
        }
        
        // 检查模型文件是否存在
        bool paramExists = assetExists(mgr, "wav2vec2_emissions.ncnn.param");
        bool binExists = assetExists(mgr, "wav2vec2_emissions.ncnn.bin");
        bool tokensExists = assetExists(mgr, "model_tokens.txt");
        
        if (!paramExists || !binExists) {
            LOGE("Model files missing! param exists: %d, bin exists: %d", paramExists, binExists);
            return false;
        }

        // 重置网络，确保初始化前是干净的状态
        net.clear();
        
        // 设置线程数和GPU加速
        int num_threads = 4;  // 使用4个线程
        bool use_gpu = false; // 默认不使用GPU
        
        // 设置NCNN选项
        ncnn::Option opt;
        opt.num_threads = num_threads;
        opt.lightmode = true;  // 使用轻量级模式
        opt.use_vulkan_compute = use_gpu;  // 是否使用GPU加速

        LOGI("Initializing with num_threads=%d, use_gpu=%d", num_threads, use_gpu);
        net.opt = opt;

        // 注册自定义层 - 只在这里注册一次
        LOGI("Registering custom layer");
        int ret_layer = net.register_custom_layer("F.scaled_dot_product_attention", ScaledDotProductAttention_layer_creator);
        if (ret_layer != 0) {
            LOGE("Failed to register custom layer, error code: %d", ret_layer);
            return false;
        }

        // ncnn模型格式，先加载param文件，再加载bin文件
        // 加载模型参数文件
        LOGI("Loading model param file: wav2vec2_emissions.ncnn.param");
        int ret = net.load_param(mgr, "wav2vec2_emissions.ncnn.param");
        if (ret != 0) {
            LOGE("Failed to load param file, error code: %d", ret);
            net.clear();
            return false;
        }
        LOGI("Successfully loaded param file");

        // 加载模型权重文件
        LOGI("Loading model bin file: wav2vec2_emissions.ncnn.bin");
        ret = net.load_model(mgr, "wav2vec2_emissions.ncnn.bin");
        if (ret != 0) {
            LOGE("Failed to load model file, error code: %d", ret);
            net.clear();
            return false;
        }
        LOGI("Successfully loaded model bin file");

        initialized = true;
        LOGI("Wav2Vec2 model successfully initialized");
        return true;
    }

    float* process(float* audioData, int length) {
        if (!initialized) {
            LOGE("Model not initialized");
            return nullptr;
        }

        if (!audioData || length <= 0) {
            LOGE("Invalid audio data or length");
            return nullptr;
        }

        // 记录输入音频信息
        LOGI("Processing audio data: length=%d samples", length);

        // 音频预处理
        std::vector<float> processed = preprocessAudio(audioData, length);
        if(processed.empty()) {
            LOGE("Audio preprocessing failed");
            return nullptr;
        }
        
        LOGI("Preprocessed audio data: length=%zu samples", processed.size());

        // 创建ncnn输入Extractor
        ncnn::Extractor ex = net.create_extractor();
        
        // 创建输入Mat
        // 注意: NCNN要求的输入尺寸取决于模型的定义
        ncnn::Mat in(processed.size(), 1);
        if (in.empty()) {
            LOGE("Failed to create input Mat");
            return nullptr;
        }
        
        // 复制数据到输入Mat
        memcpy(in.data, processed.data(), processed.size() * sizeof(float));
        
        LOGI("Created input Mat: w=%d, h=%d, c=%d, dims=%d", in.w, in.h, in.c, in.dims);
        
        // 设置输入
        LOGI("Setting input: layer=%s", wav2vec2::INPUT_LAYER);
        if(int ret = ex.input(wav2vec2::INPUT_LAYER, in)) {
            LOGE("Failed to set input: %d", ret);
            return nullptr;
        }
        
        // 提取输出
        ncnn::Mat out;
        LOGI("Extracting output: layer=%s", wav2vec2::OUTPUT_LAYER);
        if(int ret = ex.extract(wav2vec2::OUTPUT_LAYER, out)) {
            LOGE("Failed to extract output: %d", ret);
            return nullptr;
        }
        
        LOGI("Inference completed: output dimensions = [%d, %d, %d, %d]", 
            out.w, out.h, out.c, out.dims);

        if(out.empty()) {
            LOGE("Output Mat is empty");
            return nullptr;
        }

        // 分配结果内存并复制数据
        int numTokens = wav2vec2::OutputConfig::NUM_TOKENS;
        float* result = new(std::nothrow) float[numTokens];
        if (!result) {
            LOGE("Failed to allocate result memory");
            return nullptr;
        }

        LOGI("Copying output data: %d tokens", numTokens);
        // 确保不会越界复制
        int copySize = std::min(numTokens, out.w * out.h * out.c);
        memcpy(result, out.data, copySize * sizeof(float));
        
        return result;
    }

    void destroy() {
        if (initialized) {
            net.clear();
            initialized = false;
        }
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_speechenglish_Wav2Vec2_init(JNIEnv* env, jobject thiz, jobject assetManager) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to get asset manager");
        return 0;
    }

    Wav2Vec2* wav2vec2 = new(std::nothrow) Wav2Vec2();
    if (!wav2vec2) {
        LOGE("Failed to create Wav2Vec2 instance");
        return 0;
    }

    if (!wav2vec2->init(mgr)) {
        delete wav2vec2;
        return 0;
    }

    return (jlong)wav2vec2;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_speechenglish_Wav2Vec2_process(JNIEnv* env, jobject thiz, jlong handle, jfloatArray audioData) {
    Wav2Vec2* wav2vec2 = (Wav2Vec2*)handle;
    if (!wav2vec2) {
        LOGE("Invalid handle");
        return nullptr;
    }

    jsize length = env->GetArrayLength(audioData);
    jfloat* audioDataPtr = env->GetFloatArrayElements(audioData, nullptr);
    if (!audioDataPtr) {
        LOGE("Failed to get audio data");
        return nullptr;
    }

    float* result = wav2vec2->process(audioDataPtr, length);
    env->ReleaseFloatArrayElements(audioData, audioDataPtr, 0);

    if (!result) {
        LOGE("Process returned null result");
        return nullptr;
    }

    jfloatArray output = env->NewFloatArray(wav2vec2::OutputConfig::NUM_TOKENS);
    if (!output) {
        LOGE("Failed to create output array");
        delete[] result;
        return nullptr;
    }

    env->SetFloatArrayRegion(output, 0, wav2vec2::OutputConfig::NUM_TOKENS, result);
    delete[] result;
    return output;
}

JNIEXPORT void JNICALL
Java_com_example_speechenglish_Wav2Vec2_destroy(JNIEnv* env, jobject thiz, jlong handle) {
    Wav2Vec2* wav2vec2 = (Wav2Vec2*)handle;
    if (wav2vec2) {
        wav2vec2->destroy();
        delete wav2vec2;
    }
}

} 