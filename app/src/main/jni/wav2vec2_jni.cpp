#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include <android/log.h>
#include "net.h"
#include <vector>
#include <cmath>
#include "wav2vec2_model.h"
#include "force_aligner.h"
#include <sys/time.h>
#define TAG "Wav2Vec2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

double getCurrentTime() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return tv.tv_sec * 1000.0 + tv.tv_usec / 1000.0; // 返回毫秒时间戳
}

class Wav2Vec2 {
public:
    ncnn::Net net;
    ncnn::VulkanDevice* vkdev;
    bool initialized = false;
    bool useGPU = false;
    ncnn::Mat lastOutput;  // 保存最后的输出结果

    // 音频预处理参数
    static constexpr int SAMPLE_RATE = wav2vec2::ModelConfig::SAMPLE_RATE;
    static constexpr int FRAME_LENGTH = 400;  // 25ms at 16kHz
    static constexpr int FRAME_SHIFT = 320;   // 20ms at 16kHz
    static constexpr int FEATURE_DIM = wav2vec2::ModelConfig::FEATURE_DIM;

// 音频标准化函数：执行零均值和单位方差的标准化
    std::vector<float> normalizeAudio(const float* audioData, int length) {
        std::vector<float> normalizedAudio(audioData, audioData + length);

        // 计算均值
        float mean = 0.0f;
        for (int i = 0; i < length; i++) {
            mean += audioData[i];
        }
        mean /= length;

        // 计算方差
        float variance = 0.0f;
        for (int i = 0; i < length; i++) {
            float diff = audioData[i] - mean;
            variance += diff * diff;
        }
        variance /= length;

        // 计算标准差
        float stdDev = std::sqrt(variance);

        // 防止除以零
        if (stdDev < 1e-10) {
            stdDev = 1.0f;
        }

        // 执行标准化
        for (int i = 0; i < length; i++) {
            normalizedAudio[i] = (audioData[i] - mean) / stdDev;
        }

        return normalizedAudio;
    }

// 优化版本的音频标准化函数
    std::vector<float> normalizeAudioOptimized(const float* audioData, int length) {
        std::vector<float> normalizedAudio(length);

        // 使用在线算法计算均值和方差（Welford's online algorithm）
        double mean = 0.0;
        double M2 = 0.0;

#pragma omp parallel
        {
            // 每个线程的局部变量
            double local_mean = 0.0;
            double local_M2 = 0.0;
            int count = 0;

            // 并行计算每个线程的局部统计量
#pragma omp for nowait
            for (int i = 0; i < length; i++) {
                count++;
                double delta = audioData[i] - local_mean;
                local_mean += delta / count;
                double delta2 = audioData[i] - local_mean;
                local_M2 += delta * delta2;
            }

            // 合并各个线程的结果
#pragma omp critical
            {
                double delta = local_mean - mean;
                mean += delta * count / (count + length);
                M2 += local_M2 + delta * delta * count * length / (count + length);
            }
        }

        // 计算最终的标准差
        double variance = M2 / length;
        float stdDev = std::sqrt(std::max(variance, 1e-10));

        // 并行执行标准化
#pragma omp parallel for
        for (int i = 0; i < length; i++) {
            normalizedAudio[i] = (audioData[i] - mean) / stdDev;
        }

        return normalizedAudio;
    }
    
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

    Wav2Vec2() : vkdev(nullptr) {}

    ~Wav2Vec2() {
        if (vkdev) {
            delete vkdev;
            vkdev = nullptr;
        }
        if (useGPU) {
            ncnn::destroy_gpu_instance();
        }
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
        
        if (!paramExists || !binExists) {
            LOGE("Model files missing! param exists: %d, bin exists: %d", paramExists, binExists);
            return false;
        }

        // 重置网络
        net.clear();
        //TODO
        // 尝试初始化GPU失败，只用cpu了只能
//        if (ncnn::get_gpu_count() > 0) {
//            LOGI("Found %d GPU devices", ncnn::get_gpu_count());
//
//            // 创建GPU实例
//            if (ncnn::create_gpu_instance() == 0) {
//                LOGI("Successfully created GPU instance");
//
//                // 创建VulkanDevice
//                vkdev = new ncnn::VulkanDevice();
//
//                if (vkdev && vkdev->info.support_fp16_packed() && vkdev->info.support_fp16_storage()) {
//                    LOGI("Device supports FP16, enabling GPU acceleration");
//
//                    // 配置GPU选项
//                    ncnn::Option opt;
//                    opt.lightmode = true;
//                    opt.num_threads = 4;
//                    opt.use_vulkan_compute = true;
//                    opt.use_fp16_packed = true;
//                    opt.use_fp16_storage = true;
//                    opt.use_fp16_arithmetic = true;
//
//                    // 设置网络选项
//                    net.opt = opt;
//
//                    // 设置vulkan设备
//                    net.set_vulkan_device(vkdev);
//
//                    useGPU = true;
//                } else {
//                    LOGE("Device does not support required FP16 features");
//                    if (vkdev) {
//                        delete vkdev;
//                        vkdev = nullptr;
//                    }
//                    ncnn::destroy_gpu_instance();
//                }
//            } else {
//                LOGE("Failed to create GPU instance");
//            }
//        }

        // 如果GPU初始化失败，使用CPU模式
        if (!useGPU) {
            LOGI("Using CPU mode");
            ncnn::Option opt;
            opt.lightmode = true;
            opt.num_threads = 4;
            opt.use_vulkan_compute = false;
            opt.use_fp16_storage = false;
            net.opt = opt;
        }

        // 加载模型
        if (net.load_param(mgr, "wav2vec2_emissions.ncnn.param") != 0) {
            LOGE("Failed to load param file");
            return false;
        }
        if (net.load_model(mgr, "wav2vec2_emissions.ncnn.bin") != 0) {
            LOGE("Failed to load model file");
            return false;
        }

        initialized = true;
        LOGI("Model initialized with %s acceleration", useGPU ? "GPU" : "CPU");
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
//
        double PreProcessStartTime = getCurrentTime();
//        std::vector<float> processed = normalizeAudioOptimized(audioData, length);//1.126953 ms 1.221924 ms 1.304932 ms 1.244141 ms
//        std::vector<float> processed = preprocessAudio(audioData, length);// 2.360107 ms  1.0808 ms 1.3869ms 1.205078 ms 1.290039 ms
        std::vector<float> processed = normalizeAudio(audioData, length);//1.244141 ms 0.617920 ms 0.636963 ms 0.772949 ms
        double PreProcessEndTime = getCurrentTime();
        LOGI("Preprocess time: %f ms", (PreProcessEndTime - PreProcessStartTime) );
        if(processed.empty()) {
            LOGE("Audio preprocessing failed");
            return nullptr;
        }
        
        LOGI("Preprocessed audio data: length=%zu samples", processed.size());
        // 记录开始时间
        double startTime = getCurrentTime();
        // 创建推理器
        ncnn::Extractor ex = net.create_extractor();
        if (useGPU) {
            ex.set_vulkan_compute(true);
        }



        // 创建输入Mat
        ncnn::Mat in(processed.size(), processed.data(), sizeof(float), 1);
        if (in.empty()) {
            LOGE("Failed to create input Mat");
            return nullptr;
        }

        LOGI("Created input Mat: w=%d, h=%d, c=%d, dims=%d", in.w, in.h, in.c, in.dims);
        
        // 设置输入并执行推理
        if(int ret = ex.input(wav2vec2::INPUT_LAYER, in)) {
            LOGE("Failed to set input: %d", ret);
            return nullptr;
        }
        // 记录结束时间


        if(int ret = ex.extract(wav2vec2::OUTPUT_LAYER, lastOutput)) {
            LOGE("Failed to extract output: %d", ret);
            return nullptr;
        }
        double endTime = getCurrentTime();
        double inferenceTimeMs = endTime - startTime;
        LOGI("Inference completed in %.2f ms", inferenceTimeMs);

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

    float* forceAlign(const std::vector<int>& targets) {
        if (!initialized || lastOutput.empty()) {
            LOGE("Model not initialized or no output available");
            return nullptr;
        }

        LOGI("Starting force alignment with %zu targets", targets.size());

        // 检查目标序列的有效性
        for (size_t i = 0; i < targets.size(); i++) {
            if (targets[i] < 0 || targets[i] >= wav2vec2::OutputConfig::NUM_TOKENS) {
                LOGE("Invalid target token at position %zu: %d", i, targets[i]);
                return nullptr;
            }
        }

        // 使用pad token (id=0)作为blank token
        const int blank_token = 0;
        LOGI("Using pad token as blank token, id: %d", blank_token);

        speech::alignment::AlignmentResult alignment = 
            speech::alignment::ForceAligner::align(lastOutput, targets, blank_token);
        
        if (alignment.empty()) {
            LOGE("Force alignment failed");
            return nullptr;
        }

        LOGI("Force alignment completed, got %zu frames", alignment.size());
        
        // 分配结果数组：每帧包含token_id和其概率
        float* result = new(std::nothrow) float[alignment.size() * 2];
        if (!result) {
            LOGE("Failed to allocate memory for alignment result");
            return nullptr;
        }

        // 复制对齐结果
        for (size_t i = 0; i < alignment.size(); i++) {
            result[i * 2] = static_cast<float>(alignment.paths[i]);     // token_id
            result[i * 2 + 1] = alignment.scores[i];                    // probability
            
            // 验证结果
            if (std::isnan(result[i * 2 + 1]) || std::isinf(result[i * 2 + 1])) {
                LOGE("Invalid probability at frame %zu: %f", i, result[i * 2 + 1]);
                result[i * 2 + 1] = 0.0f;  // 将无效概率设为0
            }
        }

        LOGI("Alignment result prepared with %zu frames", alignment.size());
        return result;
    }

    float* forceAlignWithFeatures(const ncnn::Mat& features, const std::vector<int>& targets) {
        if (!initialized) {
            LOGE("Model not initialized");
            return nullptr;
        }

        LOGI("Starting force alignment with pre-extracted features");

        // 检查目标序列的有效性
        for (size_t i = 0; i < targets.size(); i++) {
            if (targets[i] < 0 || targets[i] >= wav2vec2::OutputConfig::NUM_TOKENS) {
                LOGE("Invalid target token at position %zu: %d", i, targets[i]);
                return nullptr;
            }
        }

        // 使用pad token (id=0)作为blank token
        const int blank_token = 0;
        LOGI("Using pad token as blank token, id: %d", blank_token);

        speech::alignment::AlignmentResult alignment = 
            speech::alignment::ForceAligner::align(features, targets, blank_token);
        
        if (alignment.empty()) {
            LOGE("Force alignment failed");
            return nullptr;
        }

        LOGI("Force alignment completed, got %zu frames", alignment.size());
        
        // 分配结果数组：每帧包含token_id和其概率
        float* result = new(std::nothrow) float[alignment.size() * 2];
        if (!result) {
            LOGE("Failed to allocate memory for alignment result");
            return nullptr;
        }

        // 复制对齐结果
        for (size_t i = 0; i < alignment.size(); i++) {
            result[i * 2] = static_cast<float>(alignment.paths[i]);     // token_id
            result[i * 2 + 1] = alignment.scores[i];                    // probability
            
            // 验证结果
            if (std::isnan(result[i * 2 + 1]) || std::isinf(result[i * 2 + 1])) {
                LOGE("Invalid probability at frame %zu: %f", i, result[i * 2 + 1]);
                result[i * 2 + 1] = 0.0f;  // 将无效概率设为0
            }
        }

        LOGI("Alignment result prepared with %zu frames", alignment.size());
        return result;
    }

    const ncnn::Mat& getLastOutput() const {
        return lastOutput;
    }

    void destroy() {
        if (initialized) {
            net.clear();
            if (useGPU) {
                ncnn::destroy_gpu_instance();
            }
            initialized = false;
            useGPU = false;
        }
    }

    bool isInitialized() const {
        return initialized;
    }

//    int preprocessAudio(const ncnn::Mat& audioMat, ncnn::Mat& preprocessed) {
//        if (audioMat.empty()) {
//            LOGE("Input audio Mat is empty");
//            return -1;
//        }
//
//        // 检查音频数据是否为单通道
//        if (audioMat.c != 1) {
//            LOGE("Input audio Mat must be single channel");
//            return -1;
//        }
//
//        // 检查音频数据是否为16kHz采样率
//        if (audioMat.w != FRAME_LENGTH) {
//            LOGE("Input audio Mat must be %d samples long", FRAME_LENGTH);
//            return -1;
//        }
//
//        // 检查音频数据是否为单精度浮点数
//        if (audioMat.elemsize != sizeof(float)) {
//            LOGE("Input audio Mat must be float32 format");
//            return -1;
//        }
//
//        // 执行音频预处理
//        std::vector<float> processed = preprocessAudio((const float*)audioMat.data, audioMat.h);
//        if (processed.empty()) {
//            LOGE("Audio preprocessing failed");
//            return -1;
//        }
//
//        // 将处理后的音频数据转换为ncnn::Mat
//        preprocessed = ncnn::Mat(processed.size(), processed.data(), sizeof(float), 1);
//        if (preprocessed.empty()) {
//            LOGE("Failed to create preprocessed Mat");
//            return -1;
//        }
//
//        return 0;
//    }
};

// 在类外定义静态常量
constexpr int Wav2Vec2::SAMPLE_RATE;
constexpr int Wav2Vec2::FRAME_LENGTH;
constexpr int Wav2Vec2::FRAME_SHIFT;
constexpr int Wav2Vec2::FEATURE_DIM;

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
Java_com_example_speechenglish_Wav2Vec2_process(
    JNIEnv* env, jobject thiz, jlong handle, jfloatArray audioData) {
    
    Wav2Vec2* wav2vec2 = reinterpret_cast<Wav2Vec2*>(handle);
    if (!wav2vec2) {
        LOGE("Invalid handle");
        return nullptr;
    }

    jsize length = env->GetArrayLength(audioData);
    jfloat* audio = env->GetFloatArrayElements(audioData, nullptr);
    if (!audio) {
        LOGE("Failed to get audio data");
        return nullptr;
    }

    float* result = wav2vec2->process(audio, length);
    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);

    if (!result) {
        LOGE("Processing failed");
        return nullptr;
    }

    const ncnn::Mat& out = wav2vec2->getLastOutput();
    int total_elements = out.total();
    
    jfloatArray resultArray = env->NewFloatArray(total_elements);
    if (resultArray) {
        env->SetFloatArrayRegion(resultArray, 0, total_elements, result);
    }

    delete[] result;
    return resultArray;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_speechenglish_Wav2Vec2_forceAlign(
    JNIEnv* env, jobject thiz, jlong native_handle, jfloatArray audio_data, jintArray target_sequence) {
    
    LOGD("Starting force alignment...");
    if (native_handle == 0) {
        LOGE("Native handle is null");
        return nullptr;
    }

    Wav2Vec2* wav2vec2 = reinterpret_cast<Wav2Vec2*>(native_handle);
    if (!wav2vec2->isInitialized()) {
        LOGE("Wav2Vec2 is not initialized");
        return nullptr;
    }

    // 获取音频数据
    jfloat* audio = env->GetFloatArrayElements(audio_data, nullptr);
    jsize audio_length = env->GetArrayLength(audio_data);
    
    // 获取目标序列
    jint* targets = env->GetIntArrayElements(target_sequence, nullptr);
    jsize target_length = env->GetArrayLength(target_sequence);

    // 创建目标序列向量
    std::vector<int> target_vec(targets, targets + target_length);

    // 处理音频数据
    float* result = wav2vec2->process(audio, audio_length);
    if (!result) {
        LOGE("Failed to process audio data");
        env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
        env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);
        return nullptr;
    }

    // 获取最后的输出特征
    const ncnn::Mat& features = wav2vec2->getLastOutput();
    if (features.empty()) {
        LOGE("No features available");
        delete[] result;
        env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
        env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);
        return nullptr;
    }

    // 执行强制对齐
    speech::alignment::AlignmentResult alignment = 
        speech::alignment::ForceAligner::align(features, target_vec, 0);

    if (alignment.empty()) {
        LOGE("Force alignment failed");
        delete[] result;
        env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
        env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);
        return nullptr;
    }

    // 创建结果数组
    int num_frames = features.h;
    jfloatArray result_array = env->NewFloatArray(num_frames * 2);
    if (!result_array) {
        LOGE("Failed to create result array");
        delete[] result;
        env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
        env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);
        return nullptr;
    }

    // 复制paths和scores到结果数组
    std::vector<float> combined_result(num_frames * 2);
    
    // 复制paths
    for (int i = 0; i < num_frames; i++) {
        combined_result[i] = static_cast<float>(alignment.paths[i]);
    }
    // 复制scores
    for (int i = 0; i < num_frames; i++) {
        combined_result[i + num_frames] = alignment.scores[i];
    }

    env->SetFloatArrayRegion(result_array, 0, num_frames * 2, combined_result.data());

    // 释放资源
    delete[] result;
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
    env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);

    return result_array;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_speechenglish_Wav2Vec2_forceAlignWithFeatures(
    JNIEnv* env, jobject thiz, jlong handle, jfloatArray features, jintArray target_sequence) {
    
    LOGD("Starting force alignment with pre-extracted features...");
    
    Wav2Vec2* wav2vec2 = reinterpret_cast<Wav2Vec2*>(handle);
    if (!wav2vec2 || !wav2vec2->isInitialized()) {
        LOGE("Invalid handle or Wav2Vec2 not initialized");
        return nullptr;
    }

    // 获取特征数据
    jfloat* feature_data = env->GetFloatArrayElements(features, nullptr);
    jsize feature_length = env->GetArrayLength(features);
    
    // 获取目标序列
    jint* targets = env->GetIntArrayElements(target_sequence, nullptr);
    jsize target_length = env->GetArrayLength(target_sequence);

    // 创建目标序列向量
    std::vector<int> target_vec(targets, targets + target_length);

    // 创建特征Mat
    ncnn::Mat feature_mat(wav2vec2::OutputConfig::NUM_TOKENS, feature_length / wav2vec2::OutputConfig::NUM_TOKENS);
    memcpy(feature_mat.data, feature_data, feature_length * sizeof(float));

    // 执行强制对齐
    float* result = wav2vec2->forceAlignWithFeatures(feature_mat, target_vec);

    // 释放JNI资源
    env->ReleaseFloatArrayElements(features, feature_data, JNI_ABORT);
    env->ReleaseIntArrayElements(target_sequence, targets, JNI_ABORT);

    if (!result) {
        LOGE("Force alignment with features failed");
        return nullptr;
    }

    // 创建返回数组
    jsize result_length = feature_length / wav2vec2::OutputConfig::NUM_TOKENS * 2;
    jfloatArray resultArray = env->NewFloatArray(result_length);
    if (resultArray) {
        env->SetFloatArrayRegion(resultArray, 0, result_length, result);
    }

    delete[] result;
    return resultArray;
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