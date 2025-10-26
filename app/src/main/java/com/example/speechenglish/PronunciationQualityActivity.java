package com.example.speechenglish;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.speech.english.phoneme.PhonemeAnalysisResult;
import com.speech.english.phoneme.PhonemeAnalyzer;
import com.speech.english.phoneme.PhonemeError;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 发音质量检测Activity
 * 集成录音、音素分析和发音质量评估功能
 */
public class PronunciationQualityActivity extends AppCompatActivity {
    
    private static final String TAG = "PronunciationQuality";

    
    // UI组件
    private EditText etTargetText;
    private Button btnAnalyze;
    private TextView tvOverallScore;
    private TextView tvMinorErrors;
    private TextView tvModerateErrors;
    private TextView tvSevereErrors;
    private TextView tvProgressText;
    private ProgressBar progressBar;
    private View cardResults;
    private View cardErrorList;
    private RecyclerView rvPronunciationErrors;
    
    // 分析相关（已移除录音）
    private Handler mainHandler;
    private ExecutorService executorService;
    private PhonemeAnalyzer phonemeAnalyzer;
    private PronunciationErrorAdapter errorAdapter;
    // 新增：音素映射器
    private PhonemeMapper phonemeMapper;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pronunciation_quality);
        
        initViews();
        initComponents();
        setupClickListeners();
    }

    /**
     * 从音频文件加载音频数据
     */

    
    private void initViews() {
        etTargetText = findViewById(R.id.et_target_text);
        btnAnalyze = findViewById(R.id.btn_analyze);
        tvOverallScore = findViewById(R.id.tv_overall_score);
        tvMinorErrors = findViewById(R.id.tv_minor_errors);
        tvModerateErrors = findViewById(R.id.tv_moderate_errors);
        tvSevereErrors = findViewById(R.id.tv_severe_errors);
        tvProgressText = findViewById(R.id.tv_progress_text);
        progressBar = findViewById(R.id.progress_bar);
        cardResults = findViewById(R.id.card_results);
        cardErrorList = findViewById(R.id.card_error_list);
        rvPronunciationErrors = findViewById(R.id.rv_pronunciation_errors);
        
        // 设置RecyclerView
        rvPronunciationErrors.setLayoutManager(new LinearLayoutManager(this));
        errorAdapter = new PronunciationErrorAdapter(new ArrayList<>());
        rvPronunciationErrors.setAdapter(errorAdapter);
        // 确保分析按钮默认可点击
        if (btnAnalyze != null) btnAnalyze.setEnabled(true);
    }
    
    private void initComponents() {
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        
        // 初始化音素分析器
        try {
            phonemeAnalyzer = new PhonemeAnalyzer();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PhonemeAnalyzer", e);
            Toast.makeText(this, "初始化音素分析器失败", Toast.LENGTH_SHORT).show();
        }
        // 新增：初始化音素映射器
        try {
            phonemeMapper = new PhonemeMapper(getAssets());
        } catch (IOException e) {
            Log.e(TAG, "初始化PhonemeMapper失败", e);
            Toast.makeText(this, "加载音素词表失败", Toast.LENGTH_SHORT).show();
        }
    }
    

    
    private void setupClickListeners() {
        // 仅保留分析按钮
        btnAnalyze.setOnClickListener(v -> analyzeRecording());
    }
    

    

    

    
    private void analyzeRecording() {
        // 直接分析资产示例练习
        long __startMs = android.os.SystemClock.elapsedRealtime();
        analyzeExerciseFromAssets("lessons/liaison/CONSONANT_VOWEL/CONSONANT_VOWEL.json");
        long __endMs = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("PronunciationQualityActivity", "analyzeExerciseFromAssets耗时: " + (__endMs - __startMs) + " ms");
    }
    

    

    

    
    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        tvProgressText.setVisibility(View.VISIBLE);
        tvProgressText.setText(message);
        btnAnalyze.setEnabled(false);
    }
    
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        tvProgressText.setVisibility(View.GONE);
        btnAnalyze.setEnabled(true);
    }
    
    private void displayAnalysisResult(PhonemeAnalysisResult result) {
        // 显示整体得分
        tvOverallScore.setText(String.format("%.1f", result.overallScore * 100));
        
        // 转换错误列表
        List<PronunciationErrorItem> errorItems = convertToErrorItems(result.errors);
        
        // 更新错误统计
        updateErrorStatistics(errorItems);
        
        // 更新错误列表
        errorAdapter.updateErrorItems(errorItems);
        
        // 显示结果卡片
        cardResults.setVisibility(View.VISIBLE);
        if (!errorItems.isEmpty()) {
            cardErrorList.setVisibility(View.VISIBLE);
        }
        
        // 根据得分设置颜色
        updateScoreColor(result.overallScore * 100);
        
        // 显示分析完成提示
        String message = errorItems.isEmpty() ? 
            "恭喜！发音非常标准！" : 
            String.format("分析完成，发现 %d 个需要改进的地方", errorItems.size());
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private List<PronunciationErrorItem> convertToErrorItems(PhonemeError[] errors) {
        List<PronunciationErrorItem> items = new ArrayList<>();
        
        if (errors != null) {
            for (PhonemeError error : errors) {
                String description = generateErrorDescription(error);
                
                PronunciationErrorItem item = new PronunciationErrorItem(
                    error.getType(),
                    error.getExpectedPhoneme(),
                    error.getActualPhoneme(),
                    error.getConfidence(),
                    error.getTimeStart(),
                    error.getTimeEnd(),
                    description
                );
                
                items.add(item);
            }
        }
        
        return items;
    }
    
    private String generateErrorDescription(PhonemeError error) {
        switch (error.getType()) {
            case "SUBSTITUTION":
                return String.format("将 /%s/ 发成了 /%s/，建议多练习这个音素的发音位置", 
                    error.getExpectedPhoneme(), error.getActualPhoneme());
            case "DELETION":
                return String.format("缺少了音素 /%s/，注意不要省略这个音", error.getExpectedPhoneme());
            case "INSERTION":
                return String.format("多发了音素 /%s/，注意控制发音节奏", error.getActualPhoneme());
            case "SUBSTITUTION_Y_LINKING":
                return "Y连读发音不准确，注意 /j/ 音与后面元音的自然连接";
            case "SUBSTITUTION_SH_LINKING":
                return "SH连读需要改进，注意 /ʃ/ 音的连读技巧";
            case "SUBSTITUTION_NASALIZATION":
                return "鼻音化发音需要改进，注意鼻音的正确位置和气流";
            case "SUBSTITUTION_WEAK":
                return "弱读发音不够准确，注意在连续语流中的弱化处理";
            case "DELETION_H_DROPPING":
                return "H音脱落，在某些连读情况下H音可以自然省略";
            case "DELETION_PLOSIVE_ELISION":
                return "爆破音省略，注意爆破音在特定环境下的弱化";
            case "DELETION_SAME_CONSONANT":
                return "相同辅音连读，两个相同辅音相遇时可以合并发音";
            default:
                return "发音需要改进，建议多加练习并注意发音细节";
        }
    }
    
    private void updateErrorStatistics(List<PronunciationErrorItem> errorItems) {
        int minorCount = 0;
        int moderateCount = 0;
        int severeCount = 0;
        
        for (PronunciationErrorItem item : errorItems) {
            switch (item.getSeverityLevel()) {
                case 0:
                    minorCount++;
                    break;
                case 1:
                    moderateCount++;
                    break;
                case 2:
                    severeCount++;
                    break;
            }
        }
        
        tvMinorErrors.setText(String.valueOf(minorCount));
        tvModerateErrors.setText(String.valueOf(moderateCount));
        tvSevereErrors.setText(String.valueOf(severeCount));
    }
    
    private void updateScoreColor(float score) {
        int color;
        if (score >= 85) {
            color = ContextCompat.getColor(this, android.R.color.holo_green_dark);
        } else if (score >= 70) {
            color = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
        } else {
            color = ContextCompat.getColor(this, android.R.color.holo_red_dark);
        }
        tvOverallScore.setTextColor(color);
    }

    // 从assets加载WAV音频为float数组（16kHz、单声道、16位）
    private float[] loadAudioFromAssets(String assetPath) {
        try {
            AssetManager am = getAssets();
            InputStream is = am.open(assetPath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            is.close();
            byte[] bytes = baos.toByteArray();

            if (bytes.length < 44 || !isValidWavHeader(bytes)) {
                throw new IOException("无效的WAV文件：" + assetPath);
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(24);
            int sampleRate = buffer.getInt();
            buffer.position(22);
            int channels = buffer.getShort() & 0xFFFF;
            buffer.position(34);
            int bitsPerSample = buffer.getShort() & 0xFFFF;

            if (sampleRate != 16000 || channels != 1 || bitsPerSample != 16) {
                throw new IOException(String.format(
                    "不支持的音频格式：采样率=%dHz，通道数=%d，位深=%d",
                    sampleRate, channels, bitsPerSample));
            }

            buffer.position(44);
            int samples = (bytes.length - 44) / 2;
            float[] audioData = new float[samples];
            for (int i = 0; i < samples; i++) {
                audioData[i] = buffer.getShort() / 32768.0f;
            }
            return audioData;
        } catch (IOException e) {
            Log.e(TAG, "从资产加载音频失败: " + assetPath, e);
            return null;
        }
    }

    // 简单校验WAV头（RIFF/WAVE）
    private boolean isValidWavHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 44) return false;
        return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }

    // 从assets读取文本内容
    private String readAssetText(String assetPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // 根据lesson JSON路径和audio_reference解析音频资产路径
    private String resolveAudioAssetPath(String lessonJsonPath, String audioRef) {
        if (audioRef == null || audioRef.isEmpty()) return null;
        try {
            getAssets().open(audioRef).close();
            Log.d(TAG, "音频引用直接存在: " + audioRef);
            return audioRef;
        } catch (IOException e) {
            // 不是根路径，尝试相对于JSON所在目录
        }
        int idx = lessonJsonPath.lastIndexOf('/');
        String baseDir = idx >= 0 ? lessonJsonPath.substring(0, idx) : "";
        String candidate = (baseDir.isEmpty() ? audioRef : baseDir + "/" + audioRef);
        try {
            getAssets().open(candidate).close();
            Log.d(TAG, "使用与JSON相同目录的音频路径: " + candidate);
            return candidate;
        } catch (IOException e) {
            Log.w(TAG, "无法解析音频资产路径: " + audioRef + " 相对于 " + lessonJsonPath);
            return null;
        }
    }

    // 解析lesson JSON并进行音素分析（资产示例）
    private void analyzeExerciseFromAssets(String lessonJsonPath) {
        showProgress("正在分析示例练习...");
        long __startMs = android.os.SystemClock.elapsedRealtime();
        executorService.execute(() -> {
            try {
                String jsonStr = readAssetText(lessonJsonPath);
                JSONObject root = new JSONObject(jsonStr);
                JSONArray exercises = root.optJSONArray("exercises");
                if (exercises == null || exercises.length() == 0) {
                    throw new JSONException("练习列表为空");
                }
                JSONObject exercise = exercises.getJSONObject(0);
                String audioRef = exercise.optString("audio_reference", "");
                String originalText = exercise.optString("original_text", "").trim();
                JSONArray originalPhones = exercise.optJSONArray("original_phones");
                if (originalPhones == null) {
                    throw new JSONException("缺少 original_phones 字段");
                }

                // 新增：计算并显示目标音素序列
                String targetPhonemesDisplay = buildPhonemeDisplay(originalPhones);
                mainHandler.post(() -> {
                    etTargetText.setText(targetPhonemesDisplay);
                    tvProgressText.setVisibility(View.VISIBLE);
                    tvProgressText.setText("已读取目标音素，开始分析...");
                });

                String[] words = originalText.isEmpty() ? new String[0] : originalText.split("\\s+");
                List<Integer> targetList = new ArrayList<>();
                int[] wordPhoneCounts = new int[originalPhones.length()];
                for (int i = 0; i < originalPhones.length(); i++) {
                    JSONArray phoneArray = originalPhones.getJSONArray(i);
                    int recognizedInWord = 0;
                    for (int j = 0; j < phoneArray.length(); j++) {
                        String phone = phoneArray.getString(j);
                        int idxPh = phonemeMapper != null ? phonemeMapper.getPhonemeIndex(phone) : -1;
                        if (idxPh >= 0) {
                            targetList.add(idxPh);
                            recognizedInWord++;
                        } else {
                            Log.w(TAG, "未知音素，跳过: " + phone);
                        }
                    }
                    wordPhoneCounts[i] = recognizedInWord;
                }
                int[] targets = new int[targetList.size()];
                for (int k = 0; k < targetList.size(); k++) targets[k] = targetList.get(k);

                String audioAssetPath = resolveAudioAssetPath(lessonJsonPath, audioRef);
                float[] audioData = null;
                if (audioAssetPath != null) {
                    audioData = loadAudioFromAssets(audioAssetPath);
                }
                if (audioData == null) {
                    Log.w(TAG, "练习音频不可用，回退到测试音频 000010069.wav");
                    audioData = loadAudioFromAssets("000010069.wav");
                }
                if (audioData == null) {
                    throw new IOException("无法加载任何音频数据用于分析");
                }


                PhonemeAnalysisResult result = phonemeAnalyzer.analyzePhonemes(
                        words,
                        targets,
                        audioData,
                        0,
                        wordPhoneCounts,
                        getAssets()
                );



                mainHandler.post(() -> {
                    hideProgress();
                    displayAnalysisResult(result);
                    // 保留目标音素显示，不再覆盖为原始文本
                });
            } catch (Exception e) {
                Log.e(TAG, "资产练习分析失败", e);
                mainHandler.post(() -> {
                    hideProgress();
                    Toast.makeText(this, "示例分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
        long __endMs = android.os.SystemClock.elapsedRealtime();
        Log.i(TAG, "analyzePhonemes耗时: " + (__endMs - __startMs) + " ms");
    }


    // 新增：将 original_phones 格式化为可读的音素序列，如 /h ə ˈloʊ/ /haʊ/
    private String buildPhonemeDisplay(JSONArray originalPhones) {
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < originalPhones.length(); i++) {
                JSONArray phoneArray = originalPhones.getJSONArray(i);
                if (i > 0) sb.append(" ");
                StringBuilder wordSb = new StringBuilder();
                for (int j = 0; j < phoneArray.length(); j++) {
                    if (j > 0) wordSb.append(" ");
                    wordSb.append(phoneArray.getString(j));
                }
                sb.append("/").append(wordSb.toString()).append("/");
            }
        } catch (JSONException e) {
            Log.w(TAG, "格式化目标音素失败", e);
        }
        return sb.toString();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放资源
        if (phonemeAnalyzer != null) {
            phonemeAnalyzer.release();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}