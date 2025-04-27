package com.example.speechenglish;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.view.View;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import android.text.TextUtils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SYSTEM_ALERT_WINDOW_REQUEST_CODE = 2;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 3;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.FOREGROUND_SERVICE
    };

    private static final String[] LEGACY_STORAGE_PERMISSIONS = {
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private Wav2Vec2 wav2Vec2;
    private TextView resultTextView;
    private TextView statusTextView;
    private ProgressBar progressBar;
    private Button testButton;
    private static final String TEST_WAV = "000010069.wav";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<String> pendingPermissions = new ArrayList<>();
    private VulkanManager vulkanManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        checkAndRequestPermissions();

        // 初始化Vulkan
        if (VulkanManager.checkVulkanSupport(this)) {
            vulkanManager = VulkanManager.getInstance();
            if (vulkanManager.isVulkanAvailable()) {
                Toast.makeText(this, "Vulkan加速已启用", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Vulkan加速已启用");
            } else {
                String errorMsg = vulkanManager.getInitErrorMessage();
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                Log.w(TAG, errorMsg);
            }
        } else {
            Log.i(TAG, "设备不支持Vulkan,将使用CPU模式");
            Toast.makeText(this, "设备不支持Vulkan,将使用CPU模式", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        resultTextView = findViewById(R.id.result_text);
        statusTextView = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.progress_bar);
        testButton = findViewById(R.id.test_button);
        
        testButton.setOnClickListener(v -> runTest());
    }

    private void showProgress(boolean show) {
        mainHandler.post(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            testButton.setEnabled(!show);
        });
    }

    private void updateStatus(String status) {
        mainHandler.post(() -> {
            statusTextView.setText(status);
            statusTextView.setVisibility(status != null && !status.isEmpty() ? 
                View.VISIBLE : View.GONE);
        });
    }

    private void checkAndRequestPermissions() {
        pendingPermissions.clear();
        
        List<String> permissionsToRequest = new ArrayList<>();
        
        // 根据Android版本选择合适的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13及以上
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
        } else { // Android 12及以下
            for (String permission : REQUIRED_PERMISSIONS) {
                if (!permission.equals(Manifest.permission.READ_MEDIA_AUDIO) &&
                    !permission.equals(Manifest.permission.READ_MEDIA_IMAGES) &&
                    !permission.equals(Manifest.permission.READ_MEDIA_VIDEO)) {
                    if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(permission);
                    }
                }
            }
            
            // 添加传统存储权限
            for (String permission : LEGACY_STORAGE_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            pendingPermissions.addAll(permissionsToRequest);
            requestNextPermission();
        } else {
            Log.d(TAG, "所有基本权限已获取，检查特殊权限");
            checkStoragePermission();
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Log.d(TAG, "请求所有文件访问权限");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e(TAG, "请求存储权限失败", e);
                    Toast.makeText(this, "无法请求存储权限: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Log.d(TAG, "已获取所有文件访问权限");
                checkSystemAlertWindowPermission();
            }
        } else {
            Log.d(TAG, "Android 10以下版本，跳过所有文件访问权限检查");
            checkSystemAlertWindowPermission();
        }
    }

    private void checkSystemAlertWindowPermission() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                Log.d(TAG, "请求悬浮窗权限");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, SYSTEM_ALERT_WINDOW_REQUEST_CODE);
            } catch (Exception e) {
                Log.e(TAG, "请求悬浮窗权限失败", e);
                Toast.makeText(this, "无法请求悬浮窗权限: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            Log.d(TAG, "已获取悬浮窗权限，初始化应用");
            initializeApp();
        }
    }

    private void requestNextPermission() {
        if (!pendingPermissions.isEmpty()) {
            String permission = pendingPermissions.get(0);
            if (!shouldShowRequestPermissionRationale(permission)) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{permission}, 
                    PERMISSION_REQUEST_CODE);
            } else {
                // 显示权限解释对话框
                showPermissionExplanationDialog(permission);
            }
        }
    }

    private void showPermissionExplanationDialog(final String permission) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("应用需要此权限才能正常工作。请在设置中授予权限。")
            .setPositiveButton("去设置", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            })
            .setNegativeButton("取消", (dialog, which) -> {
                dialog.dismiss();
                finish();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingPermissions.remove(0);
                    if (!pendingPermissions.isEmpty()) {
                        requestNextPermission();
                    } else {
                        checkStoragePermission();
                    }
                } else {
                    showPermissionExplanationDialog(permissions[0]);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        switch (requestCode) {
            case MANAGE_EXTERNAL_STORAGE_REQUEST_CODE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Log.d(TAG, "已获取存储管理权限");
                        checkSystemAlertWindowPermission();
                    } else {
                        Log.e(TAG, "用户拒绝了存储管理权限");
                        showPermissionError("存储管理");
                    }
                }
                break;
            case SYSTEM_ALERT_WINDOW_REQUEST_CODE:
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "已获取悬浮窗权限");
                    initializeApp();
                } else {
                    Log.e(TAG, "用户拒绝了悬浮窗权限");
                    showPermissionError("悬浮窗");
                }
                break;
        }
    }

    private void showPermissionError(String permissionName) {
        String message = String.format("需要%s权限才能运行应用，请在设置中手动开启", permissionName);
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            // 延迟1秒后关闭应用，让用户有时间看到提示
            new Handler().postDelayed(this::finish, 1000);
        });
    }

    private void initializeApp() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            
            // 检查应用数据目录
            String dataDirPath = getApplicationInfo().dataDir;
            File dataDir = new File(dataDirPath);
            if (!dataDir.exists()) {
                boolean created = dataDir.mkdirs();
                if (!created) {
                    String error = "无法创建应用数据目录: " + dataDirPath;
                    Log.e(TAG, error);
                    throw new RuntimeException(error);
                }
            }

            // 检查所有必需的权限
            List<String> missingPermissions = new ArrayList<>();
            
            // 基本权限检查
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            
            // 根据Android版本检查存储权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13及以上检查细分存储权限
                String[] mediaPermissions = {
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                };
                for (String permission : mediaPermissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(permission);
                    }
                }
            } else {
                // Android 12及以下检查传统存储权限
                String[] storagePermissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
                for (String permission : storagePermissions) {
                    if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(permission);
                    }
                }
            }

            if (!missingPermissions.isEmpty()) {
                String error = "缺少必要权限: " + TextUtils.join(", ", missingPermissions);
                Log.e(TAG, error);
                throw new RuntimeException(error);
            }

            // 检查assets目录中的文件
            String[] assetFiles = new String[]{
                "000010069.wav", 
                "model_tokens.txt", 
                "wav2vec2_emissions.ncnn.bin", 
                "wav2vec2_emissions.ncnn.param"
            };
            
            for (String assetFile : assetFiles) {
                try {
                    InputStream stream = getAssets().open(assetFile);
                    long size = stream.available();
                    stream.close();
                    Log.i(TAG, "Asset文件存在: " + assetFile + ", 大小: " + size + " 字节");
                } catch (IOException e) {
                    Log.e(TAG, "Asset文件不存在: " + assetFile);
                    throw new RuntimeException("缺少必要的资源文件: " + assetFile);
                }
            }

            // 初始化Wav2Vec2
            try {
                wav2Vec2 = new Wav2Vec2(this);
                if (!wav2Vec2.isInitialized()) {
                    throw new RuntimeException("Wav2Vec2初始化状态检查失败");
                }
                Log.i(TAG, "Wav2Vec2 initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Wav2Vec2初始化失败: " + e.getMessage(), e);
                wav2Vec2 = null;
                throw e;
            }

        } catch (Exception e) {
            Log.e(TAG, "初始化失败: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(this, "应用初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // 延迟1秒后关闭应用，确保用户能看到错误信息
                new Handler().postDelayed(this::finish, 1000);
            });
        }
    }

    private void showResult(String text) {
        mainHandler.post(() -> resultTextView.setText(text));
    }

//    private void showAlignmentResult(float[] alignmentResult) {
//        if (alignmentResult == null || alignmentResult.length == 0) {
//            showResult("强制对齐失败");
//            return;
//        }
//
//        StringBuilder resultBuilder = new StringBuilder();
//        resultBuilder.append("强制对齐结果:\n\n");
//        resultBuilder.append(String.format("%-10s %-10s %-10s\n", "音素", "时间(秒)", "概率"));
//        resultBuilder.append("--------------------------------\n");
//
//        // 数组前半部分是token id，后半部分是对应的时间点
//        int halfLength = alignmentResult.length / 2;
//        PhonemeMapper mapper = wav2Vec2.getPhonemeMapper();
//
//        for (int i = 0; i < halfLength; i++) {
//            int tokenId = (int) alignmentResult[i];
//            float timePoint = alignmentResult[i + halfLength];
//
//            // 使用PhonemeMapper获取音素
//            String phoneme = mapper.getPhoneme(tokenId);
//
//            resultBuilder.append(String.format("%-10s %-10.3f %-10.3f\n",
//                phoneme,           // 音素
//                timePoint,         // 时间点
//                Math.exp(timePoint) // 将对数概率转换为概率
//            ));
//        }
//
//        showResult(resultBuilder.toString());
//    }

    private void showCombinedResult(String[] decodedPhonemes, Wav2Vec2.AlignmentResult alignmentResult) {
        StringBuilder result = new StringBuilder();
        
        // 显示识别到的音素
        result.append("识别到的音素：\n\n");
        if (decodedPhonemes != null && decodedPhonemes.length > 0) {
            // 每行显示10个音素
            for (int i = 0; i < decodedPhonemes.length; i++) {
                result.append(decodedPhonemes[i]);
                if ((i + 1) % 10 == 0) {
                    result.append("\n");
                } else {
                    result.append(" ");
                }
            }
            result.append("\n\n总音素数: ").append(decodedPhonemes.length).append("\n");
        } else {
            result.append("音素识别失败\n");
        }

        // 显示强制对齐结果
        result.append("\n强制对齐结果：\n\n");
        if (alignmentResult != null && alignmentResult.paths != null && alignmentResult.timePoints != null) {
            // 获取PhonemeMapper实例
            PhonemeMapper mapper = wav2Vec2.getPhonemeMapper();
            
            // 显示表头
            result.append(String.format("%-4s  %-10s  %-10s\n", "音素", "时间(秒)", "概率"));
            result.append("------------------------------------------------\n");
            
            // 计算时间步长（假设采样率为16kHz，帧移为320个样本）
            final float FRAME_SHIFT_MS = 20.0f;  // 20ms per frame
            final float FRAME_SHIFT_S = FRAME_SHIFT_MS / 1000.0f;  // 转换为秒
            
            // 显示每个音素的时间点和概率
            for (int i = 0; i < alignmentResult.paths.length; i++) {
                int tokenId = alignmentResult.paths[i];
                float logProb = alignmentResult.timePoints[i];
                
                // 正确计算概率值：logProb本身就是对数概率，直接取exp即可
                float probability = (float) Math.exp(logProb);
                // 确保概率值在0到1之间
                probability = Math.max(0.0f, Math.min(1.0f, probability));
                
                // 计算实际时间（秒）
                float timeInSeconds = i * FRAME_SHIFT_S;
                
                // 获取音素名称
                String phoneme = mapper.getPhoneme(tokenId);
                if (phoneme == null) {
                    phoneme = "???";
                }
                
                // 显示所有音素，包括空白音素
                result.append(String.format("%-4s  %-10.3f  %-10.3f\n", 
                    phoneme,           // 音素
                    timeInSeconds,     // 开始时间（秒）
                    probability        // 概率值
                ));
            }
            
            result.append("\n注：时间基于帧移" + FRAME_SHIFT_MS + "ms计算\n");
        } else {
            result.append("强制对齐失败\n");
        }

        showResult(result.toString());
    }

    private String getConfidenceLevel(float probability) {
        if (probability >= 0.8f) {
            return "高";
        } else if (probability >= 0.5f) {
            return "中";
        } else {
            return "低";
        }
    }

    private void runTest() {
        if (!isInitialized()) {
            updateStatus("请等待初始化完成");
            return;
        }

        new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                showProgress(true);
                updateStatus("正在加载测试音频...");
                
                float[] audioData = loadWavFile(TEST_WAV);
                if (audioData == null) {
                    updateStatus("加载测试音频失败");
                    return;
                }
                
                // 进行音素识别
                updateStatus("正在进行音素识别...");
                String[] phonemes = wav2Vec2.processAndDecode(audioData);
                
                // 进行强制对齐
                updateStatus("正在进行强制对齐...");
                Wav2Vec2.AlignmentResult alignmentResult = wav2Vec2.testForceAlignment(audioData);
                
                // 显示组合结果
                if (phonemes != null || alignmentResult != null) {
                    updateStatus("处理完成");
                    showCombinedResult(phonemes, alignmentResult);
                } else {
                    updateStatus("处理失败");
                    showResult("音素识别和强制对齐均失败，请查看日志了解详细信息");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "测试过程发生错误", e);
                updateStatus("测试失败: " + e.getMessage());
                showResult("发生错误：" + e.getMessage());
            } finally {
                showProgress(false);
            }
        }).start();
    }

    private boolean isInitialized() {
        return wav2Vec2 != null && wav2Vec2.isInitialized();
    }

    private float[] loadWavFile(String filename) throws IOException {
        AssetManager am = getAssets();
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        
        try {
            updateStatus("正在读取音频文件...");
            is = am.open(filename);
            baos = new ByteArrayOutputStream();
            
            // 预分配合适的缓冲区大小
            int fileSize = is.available();
            if (fileSize <= 0) {
                throw new IOException("无效的文件大小");
            }
            baos = new ByteArrayOutputStream(fileSize);
            
            // 使用更大的缓冲区提高读取性能
            byte[] buffer = new byte[8192];
            int read;
            int totalRead = 0;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
                totalRead += read;
                if (totalRead % (fileSize / 10) == 0) {
                    updateStatus(String.format("读取音频文件... %.1f%%", 
                        (totalRead * 100.0f / fileSize)));
                }
            }
            byte[] data = baos.toByteArray();
            
            // 验证文件大小
            if (data.length < 44) {
                throw new IOException("文件太小，不是有效的WAV文件");
            }
            
            // 验证WAV文件头
            if (!isValidWavHeader(data)) {
                throw new IOException("无效的WAV文件格式");
            }
            
            // 获取采样率和通道数
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            
            // 正确的WAV文件头偏移量：
            // 采样率: 24-27字节
            // 通道数: 22-23字节
            // 位深度: 34-35字节
            bb.position(24);
            int sampleRate = bb.getInt();
            
            bb.position(22);
            int channels = bb.getShort() & 0xFFFF;  // 使用无符号短整型
            
            bb.position(34);
            int bitsPerSample = bb.getShort() & 0xFFFF;  // 使用无符号短整型
            
            Log.d(TAG, String.format("WAV文件信息: 采样率=%dHz, 通道数=%d, 位深=%d", 
                sampleRate, channels, bitsPerSample));
            
            // 打印更多调试信息
            Log.d(TAG, "WAV文件头详细信息:");
            bb.position(0);
            for (int i = 0; i < 44; i += 2) {
                Log.d(TAG, String.format("位置 %d: %02X %02X", 
                    i, data[i] & 0xFF, data[i + 1] & 0xFF));
            }
            
            if (sampleRate != 16000) {
                throw new IOException("不支持的采样率: " + sampleRate + "Hz，需要16000Hz");
            }
            if (channels != 1) {
                throw new IOException("不支持的通道数: " + channels + "，需要单通道");
            }
            if (bitsPerSample != 16) {
                throw new IOException("不支持的位深: " + bitsPerSample + "，需要16位");
            }
            
            updateStatus("正在转换音频数据...");
            
            // 跳过WAV头部（44字节）
            int headerSize = 44;
            int samples = (data.length - headerSize) / 2; // 16-bit samples
            float[] audioData = new float[samples];
            
            // 转换为float数组，使用直接缓冲区提高性能
            bb.position(headerSize);
            for (int i = 0; i < samples; i++) {
                if (i % (samples / 10) == 0) {
                    updateStatus(String.format("转换音频数据... %.1f%%", 
                        (i * 100.0f / samples)));
                }
                short sample = bb.getShort();
                audioData[i] = sample / 32768.0f; // 归一化到 [-1, 1]
            }
            
            return audioData;
            
        } catch (Exception e) {
            Log.e(TAG, "加载WAV文件失败: " + filename, e);
            throw new IOException("加载音频文件失败: " + e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w(TAG, "关闭输入流失败", e);
                }
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    Log.w(TAG, "关闭输出流失败", e);
                }
            }
        }
    }

    private boolean isValidWavHeader(byte[] data) {
        try {
            if (data.length < 44) {
                Log.e(TAG, "WAV文件头太短: " + data.length + " bytes");
                return false;
            }
            
            // 检查RIFF头
            String riff = new String(data, 0, 4);
            if (!"RIFF".equals(riff)) {
                Log.e(TAG, "无效的RIFF标记: " + riff);
                return false;
            }
            
            // 检查WAVE标记
            String wave = new String(data, 8, 4);
            if (!"WAVE".equals(wave)) {
                Log.e(TAG, "无效的WAVE标记: " + wave);
                return false;
            }
            
            // 检查fmt 子块
            String fmt = new String(data, 12, 4);
            if (!"fmt ".equals(fmt)) {
                Log.e(TAG, "无效的fmt标记: " + fmt);
                return false;
            }
            
            // 检查音频格式（PCM = 1）
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.position(20);
            short audioFormat = bb.getShort();
            if (audioFormat != 1) {
                Log.e(TAG, "不支持的音频格式: " + audioFormat);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "验证WAV文件头时出错", e);
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wav2Vec2 != null) {
            try {
                wav2Vec2.destroy();
            } catch (Exception e) {
                Log.e(TAG, "销毁Wav2Vec2时出错", e);
            }
        }
        if (vulkanManager != null) {
            vulkanManager.release();
        }
    }
} 