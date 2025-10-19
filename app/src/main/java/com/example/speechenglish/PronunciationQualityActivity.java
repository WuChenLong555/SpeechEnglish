package com.example.speechenglish;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.speech.english.phoneme.PhonemeAnalysisResult;
import com.speech.english.phoneme.PhonemeAnalyzer;
import com.speech.english.phoneme.PhonemeError;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 发音质量检测Activity
 * 集成录音、音素分析和发音质量评估功能
 */
public class PronunciationQualityActivity extends AppCompatActivity {
    
    private static final String TAG = "PronunciationQuality";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // UI组件
    private EditText etTargetText;
    private Button btnRecord;
    private Button btnAnalyze;
    private TextView tvRecordingStatus;
    private TextView tvRecordingDuration;
    private TextView tvOverallScore;
    private TextView tvMinorErrors;
    private TextView tvModerateErrors;
    private TextView tvSevereErrors;
    private TextView tvProgressText;
    private ProgressBar progressBar;
    private View cardResults;
    private View cardErrorList;
    private RecyclerView rvPronunciationErrors;
    
    // 音频录制参数
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // 录音和分析相关
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private File audioFile;
    private Handler mainHandler;
    private ExecutorService executorService;
    private PhonemeAnalyzer phonemeAnalyzer;
    private PronunciationErrorAdapter errorAdapter;
    private long recordingStartTime;
    private Runnable durationUpdateRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pronunciation_quality);
        
        initViews();
        initComponents();
        checkPermissions();
        setupClickListeners();
    }

    /**
     * 从音频文件加载音频数据
     */
    private float[] loadAudioData(File audioFile) {
        try {
            // 这里应该实现实际的音频文件读取逻辑
            // 暂时返回空数组作为占位符
            return new float[0];
        } catch (Exception e) {
            Log.e(TAG, "Failed to load audio data", e);
            return new float[0];
        }
    }
    
    private void initViews() {
        etTargetText = findViewById(R.id.et_target_text);
        btnRecord = findViewById(R.id.btn_record);
        btnAnalyze = findViewById(R.id.btn_analyze);
        tvRecordingStatus = findViewById(R.id.tv_recording_status);
        tvRecordingDuration = findViewById(R.id.tv_recording_duration);
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
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要录音权限才能使用此功能", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void setupClickListeners() {
        btnRecord.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        
        btnAnalyze.setOnClickListener(v -> analyzeRecording());
    }
    
    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予录音权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // 创建音频文件
            audioFile = new File(getCacheDir(), "recording_" + System.currentTimeMillis() + ".wav");
            
            // 初始化AudioRecord
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "录音初始化失败", Toast.LENGTH_SHORT).show();
                return;
            }
            
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            
            // 更新UI
            btnRecord.setText("停止录音");
            btnRecord.setBackgroundResource(R.drawable.button_secondary);
            btnAnalyze.setEnabled(false);
            tvRecordingStatus.setText("正在录音...");
            tvRecordingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            cardResults.setVisibility(View.GONE);
            cardErrorList.setVisibility(View.GONE);
            
            // 开始录音
            audioRecord.startRecording();
            
            // 在后台线程中处理录音数据
            executorService.execute(this::recordAudioData);
            
            // 开始更新录音时长
            updateRecordingDuration();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "开始录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void recordAudioData() {
        byte[] buffer = new byte[BUFFER_SIZE];
        List<byte[]> audioDataList = new ArrayList<>();
        
        try {
            while (isRecording && audioRecord != null) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    audioDataList.add(data);
                }
            }
            
            // 保存音频数据到文件
            saveAudioToFile(audioDataList);
            
        } catch (Exception e) {
            Log.e(TAG, "Error recording audio data", e);
            mainHandler.post(() -> {
                Toast.makeText(this, "录音数据处理失败", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void stopRecording() {
        if (!isRecording || audioRecord == null) {
            return;
        }
        
        isRecording = false;
        
        try {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            
            // 停止时长更新
            if (durationUpdateRunnable != null) {
                mainHandler.removeCallbacks(durationUpdateRunnable);
            }
            
            // 更新UI
            btnRecord.setText("开始录音");
            btnRecord.setBackgroundResource(R.drawable.button_primary);
            btnAnalyze.setEnabled(true);
            tvRecordingStatus.setText("录音完成");
            tvRecordingStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            
            Toast.makeText(this, "录音已保存", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop recording", e);
            Toast.makeText(this, "停止录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void analyzeRecording() {
        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(this, "请先录音", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String targetText = etTargetText.getText().toString().trim();
        if (targetText.isEmpty()) {
            Toast.makeText(this, "请输入目标文本", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示进度
        showProgress("正在分析发音...");
        
        // 在后台线程中进行分析
        executorService.execute(() -> {
            try {
                // 准备分析参数
                String[] words = targetText.split("\\s+");
                
                // 从录音文件读取音频数据
                float[] audioData = loadAudioData(audioFile);
                
                // 这里需要根据实际情况准备其他参数
                // 暂时使用简化的调用方式
                PhonemeAnalysisResult result = phonemeAnalyzer.analyzePhonemes(
                    words,
                    new int[0], // targets - 需要从文本转换得到
                    audioData,
                    0, // blankId
                    new int[words.length], // wordPhoneCounts
                    getAssets()
                );
                
                // 在主线程中更新UI
                mainHandler.post(() -> {
                    hideProgress();
                    displayAnalysisResult(result);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze pronunciation", e);
                mainHandler.post(() -> {
                    hideProgress();
                    Toast.makeText(this, "分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void saveAudioToFile(List<byte[]> audioDataList) {
        try (FileOutputStream fos = new FileOutputStream(audioFile)) {
            // 写入WAV文件头
            writeWavHeader(fos, audioDataList);
            
            // 写入音频数据
            for (byte[] data : audioDataList) {
                fos.write(data);
            }
            
            Log.d(TAG, "Audio saved to: " + audioFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to save audio file", e);
            mainHandler.post(() -> {
                Toast.makeText(this, "保存音频文件失败", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void writeWavHeader(FileOutputStream fos, List<byte[]> audioDataList) throws IOException {
        // 计算数据长度
        int dataLength = 0;
        for (byte[] data : audioDataList) {
            dataLength += data.length;
        }
        
        int fileLength = dataLength + 36;
        
        // WAV文件头
        fos.write("RIFF".getBytes());
        fos.write(intToByteArray(fileLength), 0, 4);
        fos.write("WAVE".getBytes());
        fos.write("fmt ".getBytes());
        fos.write(intToByteArray(16), 0, 4); // PCM格式长度
        fos.write(shortToByteArray((short) 1), 0, 2); // PCM格式
        fos.write(shortToByteArray((short) 1), 0, 2); // 单声道
        fos.write(intToByteArray(SAMPLE_RATE), 0, 4); // 采样率
        fos.write(intToByteArray(SAMPLE_RATE * 2), 0, 4); // 字节率
        fos.write(shortToByteArray((short) 2), 0, 2); // 块对齐
        fos.write(shortToByteArray((short) 16), 0, 2); // 位深度
        fos.write("data".getBytes());
        fos.write(intToByteArray(dataLength), 0, 4);
    }
    
    private byte[] intToByteArray(int value) {
        return new byte[] {
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 24) & 0xff)
        };
    }
    
    private byte[] shortToByteArray(short value) {
        return new byte[] {
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff)
        };
    }
    
    private void updateRecordingDuration() {
        durationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
                    tvRecordingDuration.setText(String.format("%02d:%02d", duration / 60, duration % 60));
                    mainHandler.postDelayed(this, 1000);
                }
            }
        };
        mainHandler.post(durationUpdateRunnable);
    }
    
    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        tvProgressText.setVisibility(View.VISIBLE);
        tvProgressText.setText(message);
        btnAnalyze.setEnabled(false);
        btnRecord.setEnabled(false);
    }
    
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        tvProgressText.setVisibility(View.GONE);
        btnAnalyze.setEnabled(true);
        btnRecord.setEnabled(true);
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止录音
        if (isRecording) {
            stopRecording();
        }
        
        // 清理Handler回调
        if (mainHandler != null && durationUpdateRunnable != null) {
            mainHandler.removeCallbacks(durationUpdateRunnable);
        }
        
        // 释放资源
        if (phonemeAnalyzer != null) {
            phonemeAnalyzer.release();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // 清理临时文件
        if (audioFile != null && audioFile.exists()) {
            audioFile.delete();
        }
    }
}