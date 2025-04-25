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

class Wav2Vec2 {
private:
    ncnn::Net net;
    bool initialized = false;
    ncnn::Mat lastOutput;  // 保存最后的输出结果

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

        // 验证输入数据
        for(int i = 0; i < length; i++) {
            if(std::isnan(audioData[i]) || std::isinf(audioData[i])) {
                LOGE("Invalid input value at position %d: %f", i, audioData[i]);
                return std::vector<float>();
            }
        }

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

        ncnn::Option opt;
        opt.num_threads = 1;  // 先用单线程测试
        opt.lightmode = false; // 关闭轻量模式进行测试
        opt.use_vulkan_compute = false;
        opt.use_fp16_storage = false; // 确保不使用fp16
        opt.use_fp16_arithmetic = false;
        net.opt = opt;
//        opt.lightmode = true;  // 使用轻量级模式
//        opt.use_vulkan_compute = use_gpu;  // 是否使用GPU加速
//
//        LOGI("Initializing with num_threads=%d, use_gpu=%d", num_threads, use_gpu);
//        net.opt = opt;

        // ncnn模型格式，先加载param文件，再加载bin文件
        // 加载模型参数文件
        LOGI("Loading model param file: wav2vec2_emissions.ncnn.param");
        int ret = net.load_param(mgr, "wav2vec2_emissions.ncnn.param");
        if (ret != 0) {
            LOGE("Failed to load param file, error code: %d", ret);
            return false;
        }

        // 加载模型权重文件
        LOGI("Loading model bin file");
        ret = net.load_model(mgr, "wav2vec2_emissions.ncnn.bin");
        if (ret != 0) {
            LOGE("Failed to load model file, error code: %d", ret);
            return false;
        }
        LOGI("Successfully loaded model bin file");

        initialized = true;
        LOGI("Wav2Vec2 model successfully initialized");
        const std::vector<int>& input_indexes = net.input_indexes();
        const std::vector<int>& output_indexes = net.output_indexes();

        LOGI("Network structure: input_layers=%d, output_layers=%d",
             (int)input_indexes.size(), (int)output_indexes.size());

        #if NCNN_STRING
        const std::vector<const char*>& input_names = net.input_names();
        const std::vector<const char*>& output_names = net.output_names();

        for(size_t i = 0; i < input_names.size(); i++) {
            LOGI("Input layer %d: %s", (int)i, input_names[i]);
        }
        for(size_t i = 0; i < output_names.size(); i++) {
            LOGI("Output layer %d: %s", (int)i, output_names[i]);
        }
        #endif
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
        ncnn::Mat in(processed.size(), processed.data(), sizeof(float), 1);
        if (in.empty()) {
            LOGE("Failed to create input Mat");
            return nullptr;
        }

        LOGI("Created input Mat: w=%d, h=%d, c=%d, dims=%d", in.w, in.h, in.c, in.dims);
        
        // 设置输入
        LOGI("Setting input: layer=%s", wav2vec2::INPUT_LAYER);
        if(int ret = ex.input(wav2vec2::INPUT_LAYER, in)) {
            LOGE("Failed to set input: %d", ret);
            return nullptr;
        }
        
        // 提取输出
        LOGI("Extracting output: layer=%s", wav2vec2::OUTPUT_LAYER);
        if(int ret = ex.extract(wav2vec2::OUTPUT_LAYER, lastOutput)) {
            LOGE("Failed to extract output: %d", ret);
            return nullptr;
        }
        
        LOGI("Inference completed: output dimensions = [%d, %d, %d, %d]", 
            lastOutput.w, lastOutput.h, lastOutput.c, lastOutput.dims);

        if(lastOutput.empty()) {
            LOGE("Output Mat is empty");
            return nullptr;
        }

        // 验证输出数据
        float* output_data = (float*)lastOutput.data;
        int total_elements = lastOutput.w * lastOutput.h * lastOutput.c;
        
        // 检查输出中是否有无效值
        for(int i = 0; i < total_elements; i++) {
            if(std::isnan(output_data[i]) || std::isinf(output_data[i])) {
                LOGE("Invalid output value at position %d: %f", i, output_data[i]);
                output_data[i] = -std::numeric_limits<float>::infinity();  // 将无效值设为负无穷
            }
        }

        // 分配结果内存并复制完整的输出数据
        float* result = new(std::nothrow) float[total_elements];
        if (!result) {
            LOGE("Failed to allocate result memory for %d elements", total_elements);
            return nullptr;
        }

        LOGI("Copying complete output data: %d tokens × %d time steps = %d elements", 
            wav2vec2::OutputConfig::NUM_TOKENS, lastOutput.w, total_elements);
        memcpy(result, lastOutput.data, total_elements * sizeof(float));
        
        return result;
    }

    const ncnn::Mat& getLastOutput() const {
        return lastOutput;
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

    // 从ncnn::Mat的维度获取实际的输出大小
    const ncnn::Mat& out = wav2vec2->getLastOutput();
    int total_elements = out.w * out.h * out.c;
    LOGI("Creating output array with %d elements (time_steps=%d × num_tokens=%d)", 
        total_elements, out.w, wav2vec2::OutputConfig::NUM_TOKENS);

    jfloatArray output = env->NewFloatArray(total_elements);
    if (!output) {
        LOGE("Failed to create output array of size %d", total_elements);
        delete[] result;
        return nullptr;
    }

    env->SetFloatArrayRegion(output, 0, total_elements, result);
    delete[] result;
    
    LOGI("Successfully transferred %d elements to Java", total_elements);
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