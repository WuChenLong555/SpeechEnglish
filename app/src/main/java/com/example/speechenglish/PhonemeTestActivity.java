package com.example.speechenglish;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.speech.english.liaison.LiaisonRules;
import com.speech.english.liaison.LiaisonType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PhonemeTestActivity extends AppCompatActivity {
    private static final String TAG = "PhonemeTestActivity";
    private TextView tvAudioInfo;
    private TextView tvPhonemeSequence;
    private RecyclerView rvAlignmentResults;
    private RecyclerView rvLiaisonResults;
    private LineChart chartFeatures;
    private Button btnStartTest;

    private Wav2Vec2 wav2vec2;
    private static final String TEST_AUDIO_FILE = "000010069.wav"; // 修改为实际的音频文件名
    private static final String TEST_TEXT = "tɑːm ɡɪvz ʌp bɑːksɪŋ";
    private static final String[] TEST_PHONEMES = {
        "t", "ɑː", "m", "ɡ", "ɪ", "v", "z",
        "ʌ", "p", "b", "ɑː", "k", "s", "ɪ", "ŋ"
    };
    
    /**
     * 将音素序列文本转换为单个音素数组
     * @param phonemeText 音素序列文本
     * @return 单个音素数组
     */
    private String[] splitPhonemeSequence(String phonemeText) {
        List<String> phonemes = new ArrayList<>();
        int i = 0;
        while (i < phonemeText.length()) {
            // 检查长元音（如ɑː）
            if (i < phonemeText.length() - 1 && phonemeText.charAt(i + 1) == 'ː') {
                phonemes.add(phonemeText.substring(i, i + 2));
                i += 2;
            } 
            // 跳过空格
            else if (Character.isWhitespace(phonemeText.charAt(i))) {
                i++;
            }
            // 处理单个音素
            else {
                phonemes.add(String.valueOf(phonemeText.charAt(i)));
                i++;
            }
        }
        return phonemes.toArray(new String[0]);
    }
    
    // 保存提取的特征，以便后续使用
    private float[] extractedFeatures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phoneme_test);

        // 初始化视图
        initViews();
        // 初始化Wav2Vec2模型
        initWav2Vec2();
        // 设置点击事件
        setupClickListeners();
        // 显示音素序列
        displayPhonemeSequence();
    }

    private void initViews() {
        tvAudioInfo = findViewById(R.id.tv_audio_info);
        tvPhonemeSequence = findViewById(R.id.tv_phoneme_sequence);
        rvAlignmentResults = findViewById(R.id.rv_alignment_results);
        rvLiaisonResults = findViewById(R.id.rv_liaison_results);
        chartFeatures = findViewById(R.id.chart_features);
        btnStartTest = findViewById(R.id.btn_start_test);

        // 设置RecyclerView
        rvAlignmentResults.setLayoutManager(new LinearLayoutManager(this));
        rvLiaisonResults.setLayoutManager(new LinearLayoutManager(this));

        // 初始化图表
        setupChart();
    }

    private void displayPhonemeSequence() {
        StringBuilder sequence = new StringBuilder("音素序列：\n");
        for (int i = 0; i < TEST_PHONEMES.length; i++) {
            sequence.append(TEST_PHONEMES[i]);
            if (i < TEST_PHONEMES.length - 1) {
                sequence.append(" → ");
            }
        }
        tvPhonemeSequence.setText(sequence.toString());
    }

    private void initWav2Vec2() {
        try {
            wav2vec2 = new Wav2Vec2(this);
            if (!wav2vec2.isInitialized()) {
                Toast.makeText(this, "Wav2Vec2初始化失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Wav2Vec2初始化错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupClickListeners() {
        btnStartTest.setOnClickListener(v -> startTest());
    }

    private void setupChart() {
        chartFeatures.getDescription().setEnabled(false);
        chartFeatures.setTouchEnabled(true);
        chartFeatures.setDragEnabled(true);
        chartFeatures.setScaleEnabled(true);
        chartFeatures.setPinchZoom(true);
    }

    private void startTest() {
        try {
            // 1. 加载测试音频
            float[] audioData = loadAudioFromAssets(TEST_AUDIO_FILE);
            if (audioData == null) {
                Toast.makeText(this, "音频加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 显示音频信息
            tvAudioInfo.setText(String.format("音频文件：%s\n采样数：%d", TEST_AUDIO_FILE, audioData.length));

            // 2. 提取特征
            extractedFeatures = wav2vec2.process(audioData);
            if (extractedFeatures == null) {
                Toast.makeText(this, "特征提取失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "特征提取成功，特征维度: " + extractedFeatures.length);

            // 3. 将音素序列转换为ID
            String[] phonemes = splitPhonemeSequence(TEST_TEXT);
            int[] phonemeIds = new int[phonemes.length];
            PhonemeMapper mapper = wav2vec2.getPhonemeMapper();
            phonemeIds = mapper.phonemesToIndices(phonemes);

            // 4. 使用提取的特征进行强制对齐
            float[] alignmentResult = wav2vec2.forceAlignWithFeatures(extractedFeatures, phonemeIds);
            if (alignmentResult == null) {
                Toast.makeText(this, "强制对齐失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "强制对齐成功，结果维度: " + alignmentResult.length);

            // 5. 处理对齐结果
            processAlignmentResult(alignmentResult);

            // 6. 分析连读位置
            analyzeLiaisonPositions(alignmentResult, extractedFeatures);

            // 7. 显示声学特征
            displayAcousticFeatures(extractedFeatures);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "测试过程出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private float[] loadAudioFromAssets(String filename) {
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open(filename);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            // 检查WAV文件头
            if (bytes.length < 44 || !isValidWavHeader(bytes)) {
                throw new IOException("无效的WAV文件格式");
            }

            // 获取音频参数
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

            // 转换为float数组
            buffer.position(44); // 跳过WAV头
            float[] audioData = new float[(bytes.length - 44) / 2];
            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = buffer.getShort() / 32768.0f;
            }

            return audioData;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isValidWavHeader(byte[] data) {
        if (data.length < 44) return false;
        
        String riff = new String(data, 0, 4);
        String wave = new String(data, 8, 4);
        String fmt = new String(data, 12, 4);
        
        return "RIFF".equals(riff) && "WAVE".equals(wave) && "fmt ".equals(fmt);
    }

    private void processAlignmentResult(float[] alignmentResult) {
        if (alignmentResult == null || alignmentResult.length == 0) {
            Log.e(TAG, "对齐结果为空");
            return;
        }

        // 解析对齐结果
        int numFrames = alignmentResult.length / 2;  // 每帧包含音素ID和得分
        List<PhonemeAlignmentItem> alignmentItems = new ArrayList<>();
        PhonemeMapper mapper = wav2vec2.getPhonemeMapper();
        
        for (int i = 0; i < numFrames; i++) {
            int phonemeId = (int) alignmentResult[i * 2];
            float score = alignmentResult[i * 2 + 1];
            float timePoint = i * 0.02f;  // 假设帧移为20ms
            String phoneme = mapper.getPhoneme(phonemeId);
            
            // 跳过<pad>标记（ID为0）
            if (phonemeId != 0) {
                alignmentItems.add(new PhonemeAlignmentItem(phonemeId, phoneme, score, timePoint));
            }
        }

        // 创建并设置适配器
        PhonemeAlignmentAdapter adapter = new PhonemeAlignmentAdapter(alignmentItems);
        rvAlignmentResults.setAdapter(adapter);
    }

    private void analyzeLiaisonPositions(float[] alignmentResult, float[] features) {
        if (alignmentResult == null || alignmentResult.length == 0) {
            Log.e(TAG, "对齐结果为空，无法分析连读位置");
            return;
        }

        List<LiaisonItem> liaisonItems = new ArrayList<>();
        PhonemeMapper mapper = wav2vec2.getPhonemeMapper();

        // 获取单词边界的音素
        String[] words = TEST_TEXT.split(" ");
        List<WordBoundaryPhonemes> wordBoundaries = new ArrayList<>();
        
        int currentIndex = 0;
        for (String word : words) {
            String[] wordPhonemes = splitPhonemeSequence(word);
            if (wordPhonemes.length > 0) {
                // 如果不是第一个单词，添加这个单词的第一个音素
                if (currentIndex > 0) {
                    wordBoundaries.add(new WordBoundaryPhonemes(
                        words[currentIndex - 1],  // 前一个单词
                        words[currentIndex],      // 当前单词
                        getLastPhoneme(words[currentIndex - 1]),  // 前一个单词的最后一个音素
                        wordPhonemes[0]          // 当前单词的第一个音素
                    ));
                }
            }
            currentIndex++;
        }

        // 分析单词边界的连读情况
        for (WordBoundaryPhonemes boundary : wordBoundaries) {
            // 检查连读规则
            LiaisonType liaisonType = LiaisonRules.checkLiaison(
                boundary.lastPhonemeOfPrevWord,
                boundary.firstPhonemeOfNextWord,
                boundary.prevWord  // 提供单词信息
            );
            
            // 如果存在连读可能，添加到列表
            if (liaisonType != null) {
                // 找到对应的时间点（通过对齐结果）
                float timePoint = findTimePointForPhonemes(
                    alignmentResult,
                    mapper,
                    boundary.lastPhonemeOfPrevWord,
                    boundary.firstPhonemeOfNextWord
                );
                
                String description = String.format("%s|%s 之间: %s",
                    boundary.prevWord,
                    boundary.nextWord,
                    getDescriptionForLiaisonType(liaisonType,
                        boundary.lastPhonemeOfPrevWord,
                        boundary.firstPhonemeOfNextWord)
                );
                
                liaisonItems.add(new LiaisonItem(
                    description,
                    timePoint,
                    1.0f  // 这里可以根据对齐得分计算一个置信度
                ));
            }
        }

        // 创建并设置适配器
        LiaisonAdapter adapter = new LiaisonAdapter(liaisonItems);
        rvLiaisonResults.setAdapter(adapter);
    }

    // 用于存储单词边界音素信息的辅助类
    private static class WordBoundaryPhonemes {
        String prevWord;
        String nextWord;
        String lastPhonemeOfPrevWord;
        String firstPhonemeOfNextWord;

        WordBoundaryPhonemes(String prevWord, String nextWord, 
                            String lastPhoneme, String firstPhoneme) {
            this.prevWord = prevWord;
            this.nextWord = nextWord;
            this.lastPhonemeOfPrevWord = lastPhoneme;
            this.firstPhonemeOfNextWord = firstPhoneme;
        }
    }

    // 获取单词的最后一个音素
    private String getLastPhoneme(String word) {
        String[] phonemes = splitPhonemeSequence(word);
        return phonemes.length > 0 ? phonemes[phonemes.length - 1] : "";
    }

    // 在对齐结果中找到两个音素交界处的时间点
    private float findTimePointForPhonemes(float[] alignmentResult, 
                                         PhonemeMapper mapper,
                                         String phoneme1, 
                                         String phoneme2) {
        int phoneme1Id = mapper.getPhonemeIndex(phoneme1);
        int phoneme2Id = mapper.getPhonemeIndex(phoneme2);
        
        // 在对齐结果中查找这两个音素相邻的位置
        for (int i = 0; i < alignmentResult.length / 2 - 1; i++) {
            int currentId = (int) alignmentResult[i * 2];
            int nextId = (int) alignmentResult[(i + 1) * 2];
            
            if (currentId == phoneme1Id && nextId == phoneme2Id) {
                return i * 0.02f;  // 假设帧移为20ms
            }
        }
        
        return 0.0f;  // 如果没找到，返回0
    }

    private String getDescriptionForLiaisonType(LiaisonType type, String phoneme1, String phoneme2) {
        switch (type) {
            case VOWEL_VOWEL:
                return String.format("元音连读: %s → %s", phoneme1, phoneme2);
            case CONSONANT_VOWEL:
                return String.format("辅音+元音连读: %s → %s", phoneme1, phoneme2);
            case PLOSIVE_ELISION:
                return String.format("爆破音省略: %s → %s", phoneme1, phoneme2);
            case H_DROPPING:
                return String.format("h音脱落(非句首/停顿且前为辅音): %s → %s", phoneme1, phoneme2);
            case R_LINKING:
                return String.format("r连音: %s → %s", phoneme1, phoneme2);
            case J_LINKING:
                return String.format("j连音: %s → %s", phoneme1, phoneme2);
            case W_LINKING:
                return String.format("w连音: %s → %s", phoneme1, phoneme2);
            case SAME_CONSONANT:
                return String.format("相同辅音连读: %s → %s", phoneme1, phoneme2);
            case SH_LIAISON:
                return String.format("SH连读: %s → %s", phoneme1, phoneme2);
            case Y_LIAISON:
                return String.format("Y连读: %s → %s", phoneme1, phoneme2);
            case LIQUID_VOWEL:
                return String.format("流音+元音: %s → %s", phoneme1, phoneme2);
            case NASAL_VOWEL:
                return String.format("鼻音+元音: %s → %s", phoneme1, phoneme2);
            case FRICATIVE_VOWEL:
                return String.format("摩擦音+元音: %s → %s", phoneme1, phoneme2);
            case PLOSIVE_VOWEL:
                return String.format("爆破音+元音: %s → %s", phoneme1, phoneme2);
            case NASALIZATION:
                return String.format("鼻化: %s → %s", phoneme1, phoneme2);
            case SCHWA:
                return String.format("中元音弱化: %s → %s", phoneme1, phoneme2);
            default:
                return String.format("%s → %s", phoneme1, phoneme2);
        }
    }

    private void displayAcousticFeatures(float[] features) {
        if (features == null || features.length == 0) {
            Log.e(TAG, "特征数据为空");
            return;
        }

        // 创建数据点
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < features.length; i++) {
            entries.add(new Entry(i, features[i]));
        }

        // 创建数据集
        LineDataSet dataSet = new LineDataSet(entries, "声学特征");
        dataSet.setDrawCircles(false);
        dataSet.setColor(getResources().getColor(R.color.purple_500));
        dataSet.setLineWidth(1f);

        // 设置数据
        LineData lineData = new LineData(dataSet);
        chartFeatures.setData(lineData);
        chartFeatures.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wav2vec2 != null) {
            wav2vec2.destroy();
        }
    }
}