package com.example.speechenglish;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

/**
 * 练习界面 - 实现音频播放和录音功能，支持多练习流程
 */
public class ExerciseActivity extends AppCompatActivity implements ExerciseManager.ExerciseStateListener {
    private static final String TAG = "ExerciseActivity";
    
    // UI组件
    private TextView lessonTitleTextView;
    private TextView originalTextView;
    private TextView phonemeTextView;
    private Button playPauseButton;
    private Button recordButton;
    private LinearLayout scoreLayout;
    private TextView scoreTextView;
    
    // 新增的多练习UI组件
    private LinearLayout exerciseNavigationLayout;
    private TextView exerciseProgressText;
    private TextView courseCompletionText;
    private TextView currentExerciseTitleText;
    private Button btnPreviousExercise;
    private Button btnNextExercise;
    private LinearLayout exerciseCompletionActions;
    private Button btnRetryExercise;
    private Button btnContinueNext;
    
    // 音频播放相关
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    
    // 录音相关
    private boolean isRecording = false;
    
    // 课程数据
    private String lessonId;
    private String lessonTitle;
    private LessonManager.Exercise currentExercise;
    
    // 练习管理器
    private ExerciseManager exerciseManager;
    
    // 导航参数
    private String fromActivity;
    private boolean isSubCourse;
    
    // 手势检测
    private GestureDetector gestureDetector;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise);
        
        // 设置ActionBar返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // 获取传递的课程信息
        Intent intent = getIntent();
        lessonId = intent.getStringExtra("lesson_id");
        lessonTitle = intent.getStringExtra("lesson_title");
        
        // 获取导航参数
        fromActivity = intent.getStringExtra("from_activity");
        isSubCourse = intent.getBooleanExtra("is_sub_course", false);
        
        // 初始化UI组件
        initViews();
        
        // 设置点击事件
        setupClickListeners();
        
        // 初始化手势检测
        setupGestureDetector();
        
        // 加载练习数据
        loadExerciseData();
    }
    
    private void initViews() {
        lessonTitleTextView = findViewById(R.id.lesson_title);
        originalTextView = findViewById(R.id.original_text);
        phonemeTextView = findViewById(R.id.phoneme_text);
        playPauseButton = findViewById(R.id.btn_play_pause);
        recordButton = findViewById(R.id.btn_record);
        scoreLayout = findViewById(R.id.score_layout);
        scoreTextView = findViewById(R.id.score_text);
        
        // 初始化新的多练习UI组件
        exerciseNavigationLayout = findViewById(R.id.exercise_navigation_layout);
        exerciseProgressText = findViewById(R.id.exercise_progress_text);
        courseCompletionText = findViewById(R.id.course_completion_text);
        currentExerciseTitleText = findViewById(R.id.current_exercise_title);
        btnPreviousExercise = findViewById(R.id.btn_previous_exercise);
        btnNextExercise = findViewById(R.id.btn_next_exercise);
        exerciseCompletionActions = findViewById(R.id.exercise_completion_actions);
        btnRetryExercise = findViewById(R.id.btn_retry_exercise);
        btnContinueNext = findViewById(R.id.btn_continue_next);
        
        // 设置课程标题
        if (lessonTitle != null) {
            lessonTitleTextView.setText(lessonTitle);
        }
        
        // 确保导航按钮在初始化时就可见
        if (exerciseNavigationLayout != null) {
            exerciseNavigationLayout.setVisibility(View.VISIBLE);
        }
        if (btnPreviousExercise != null) {
            btnPreviousExercise.setVisibility(View.VISIBLE);
        }
        if (btnNextExercise != null) {
            btnNextExercise.setVisibility(View.VISIBLE);
        }
    }
    
    private void setupClickListeners() {
        playPauseButton.setOnClickListener(v -> {
            if (isPlaying) {
                pauseAudio(); // 点击暂停播放
            } else {
                // 检查是否是从暂停状态恢复播放（MediaPlayer存在且处于暂停状态）
                if (mediaPlayer != null && isPaused) {
                    resumeAudio(); // 恢复播放
                } else {
                    playAudio(); // 开始新的播放
                }
            }
        });
        
        // 设置按住录音功能
        recordButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startRecording();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopRecording();
                    return true;
            }
            return false;
        });
        
        // 设置练习导航按钮点击事件
        btnPreviousExercise.setOnClickListener(v -> {
            if (exerciseManager != null) {
                exerciseManager.goToPreviousExercise();
            }
        });
        
        btnNextExercise.setOnClickListener(v -> {
            if (exerciseManager != null) {
                exerciseManager.goToNextExercise();
            }
        });
        
        // 设置练习完成后的操作按钮
        btnRetryExercise.setOnClickListener(v -> {
            retryCurrentExercise();
        });
        
        btnContinueNext.setOnClickListener(v -> {
            if (exerciseManager != null) {
                exerciseManager.goToNextExercise();
            }
        });
    }
    
    /**
     * 设置手势检测器
     */
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // 水平滑动
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 右滑（左滑手势）
                            onSwipeRight();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }
    
    /**
     * 处理右滑手势（用户从左向右滑动）
     */
    private void onSwipeRight() {
        if (isSubCourse) {
            // 如果是子课程，右滑返回子课程列表
            handleBackNavigation();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }
    
    private void loadExerciseData() {
        // 从LessonManager加载练习数据
        LessonManager lessonManager = LessonManager.getInstance(this);
        
        try {
            // 加载课程详细数据
            LessonManager.Lesson lesson = lessonManager.loadLessonDetails(lessonId);
            
            if (lesson != null && lesson.getExercises() != null && !lesson.getExercises().isEmpty()) {
                 // 初始化ExerciseManager
                 exerciseManager = new ExerciseManager(this, lessonId);
                 
                 // 重要：初始化练习数据
                 exerciseManager.initializeExercises(lesson.getExercises());
                 
                 // 设置监听器
                 exerciseManager.setExerciseStateListener(this);
                 
                 // 获取当前练习
                 currentExercise = exerciseManager.getCurrentExercise();
                 if (currentExercise != null) {
                     Log.d(TAG, "成功加载练习数据: " + currentExercise.getTitle());
                 } else {
                     Log.w(TAG, "ExerciseManager初始化后getCurrentExercise()返回null");
                 }
             } else {
                 // 如果没有练习数据，使用示例数据
                 Log.w(TAG, "课程中没有练习数据，使用示例数据");
                 createSampleExercise();
             }
            
        } catch (Exception e) {
            Log.e(TAG, "加载课程详细数据失败: " + lessonId, e);
            // 加载失败时使用示例数据
            createSampleExercise();
        }
        
        // 更新UI显示
        updateExerciseDisplay();
        updateNavigationUI();
    }
    
    private void createSampleExercise() {
        // 创建示例练习数据
        currentExercise = new LessonManager.Exercise(
            "ex_001",
            "基础问候语练习",
            "/həˈloʊ, haʊ ɑr ju təˈdeɪ/",
            "Hello, how are you today?",
            "audio/hello_greeting.mp3"
        );
        
        // 为示例数据创建ExerciseManager
        java.util.List<LessonManager.Exercise> sampleExercises = new java.util.ArrayList<>();
        sampleExercises.add(currentExercise);
        exerciseManager = new ExerciseManager(this, lessonId);
        
        // 重要：初始化练习数据
        exerciseManager.initializeExercises(sampleExercises);
        
        // 设置监听器
        exerciseManager.setExerciseStateListener(this);
    }
    
    private void updateExerciseDisplay() {
        if (currentExercise != null) {
            originalTextView.setText(currentExercise.getExampleWords());
            phonemeTextView.setText(currentExercise.getTargetPhoneme());
            
            // 更新当前练习标题
            if (currentExerciseTitleText != null) {
                currentExerciseTitleText.setText(currentExercise.getTitle());
            }
        }
        
        // 立即更新导航UI，显示上一个和下一个按钮
        updateNavigationUI();
    }
    
    /**
     * 更新导航UI显示
     */
    private void updateNavigationUI() {
        if (exerciseManager == null) return;
        
        // 更新练习进度文本
        if (exerciseProgressText != null) {
            int currentIndex = exerciseManager.getCurrentExerciseIndex() + 1;
            int totalCount = exerciseManager.getTotalExerciseCount();
            exerciseProgressText.setText(currentIndex + "/" + totalCount);
        }
        
        // 更新课程完成度
        if (courseCompletionText != null) {
            CourseProgress courseProgress = exerciseManager.getCourseProgress();
            if (courseProgress != null) {
                int completionPercentage = Math.round(courseProgress.getCompletionPercentage());
                courseCompletionText.setText("完成度: " + completionPercentage + "%");
            }
        }
        
        // 更新导航按钮状态
        if (btnPreviousExercise != null) {
            btnPreviousExercise.setEnabled(exerciseManager.hasPreviousExercise());
        }
        
        if (btnNextExercise != null) {
            btnNextExercise.setEnabled(exerciseManager.hasNextExercise());
        }
    }
    
    /**
     * 重试当前练习
     */
    private void retryCurrentExercise() {
        if (exerciseManager != null && currentExercise != null) {
            // 检查是否允许重试
            if (exerciseManager.canRetryExercise()) {
                exerciseManager.retryExercise();
                
                // 重置UI状态
                scoreTextView.setText("得分: --");
                scoreLayout.setVisibility(View.GONE);
                exerciseCompletionActions.setVisibility(View.GONE);
                
                // 重新开始练习
                updateExerciseDisplay();
                
                Log.d(TAG, "重试当前练习: " + currentExercise.getExerciseId());
                Toast.makeText(this, "重新开始练习", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "该练习不允许重试", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // 实现ExerciseStateListener接口方法
    @Override
    public void onExerciseChanged(LessonManager.Exercise exercise) {
        currentExercise = exercise;
        updateExerciseDisplay();
        updateNavigationUI();
        
        // 隐藏评分结果和完成操作
        if (scoreLayout != null) {
            scoreLayout.setVisibility(View.GONE);
        }
        if (exerciseCompletionActions != null) {
            exerciseCompletionActions.setVisibility(View.GONE);
        }
        
        Log.d(TAG, "切换到练习: " + exercise.getTitle());
    }
    
    @Override
    public void onExerciseCompleted(String exerciseId, int score) {
        Log.d(TAG, "练习完成: " + exerciseId + ", 得分: " + score);
        
        // 显示完成操作按钮
        if (exerciseCompletionActions != null) {
            exerciseCompletionActions.setVisibility(View.VISIBLE);
        }
        
        // 更新导航UI
        updateNavigationUI();
        
        Toast.makeText(this, "练习完成！得分: " + score, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onCourseCompleted(String courseId) {
        Log.d(TAG, "课程完成: " + courseId);
        
        // 更新导航UI
        updateNavigationUI();
        
        Toast.makeText(this, "恭喜！课程全部完成！", Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onProgressUpdated(CourseProgress courseProgress) {
        Log.d(TAG, "进度更新: " + courseProgress.getCompletionPercentage() + "%");
        
        // 更新导航UI
        updateNavigationUI();
    }
    
    public void onNavigationStateChanged(boolean hasPrevious, boolean hasNext) {
        // 更新导航按钮状态
        if (btnPreviousExercise != null) {
            btnPreviousExercise.setEnabled(hasPrevious);
        }
        if (btnNextExercise != null) {
            btnNextExercise.setEnabled(hasNext);
        }
        
        Log.d(TAG, "导航状态更新: 上一个=" + hasPrevious + ", 下一个=" + hasNext);
    }
    
    private void playAudio() {
        try {
            // 重置MediaPlayer状态
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }
            
            // 设置完成监听器 - 播放完毕立刻重置
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 立刻重置到初始状态
                    isPlaying = false;
                    isPaused = false; // 清除暂停状态
                    playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0);
                    
                    // 重置MediaPlayer以便下次播放
                    if (mediaPlayer != null) {
                        mediaPlayer.reset();
                    }
                }
            });
            
            // 使用练习数据中的音频文件路径
            String audioFileName = null;
            if (currentExercise != null && currentExercise.getAudioPath() != null && !currentExercise.getAudioPath().isEmpty()) {
                audioFileName = currentExercise.getAudioPath();
            }
            
            if (audioFileName != null) {
                // 从assets中加载音频文件
                AssetFileDescriptor afd = getAssets().openFd("lessons/liaison/CONSONANT_VOWEL/" + audioFileName);
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mediaPlayer.prepare();
                
                mediaPlayer.start();
                isPlaying = true;
                isPaused = false; // 清除暂停状态
                playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_pause, 0, 0); // 使用暂停图标
                
                Log.d(TAG, "开始播放音频: " + audioFileName);
                Toast.makeText(this, "播放音频: " + audioFileName, Toast.LENGTH_SHORT).show();
            } else {
                throw new IOException("音频文件路径为空");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "播放音频失败", e);
            Toast.makeText(this, "音频播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // 如果音频播放失败，显示演示模式
            showDemoPlayback();
        } catch (Exception e) {
            Log.e(TAG, "音频播放异常", e);
            Toast.makeText(this, "音频播放异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // 如果音频播放失败，显示演示模式
            showDemoPlayback();
        }
    }
    
    private void showDemoPlayback() {
        // 演示模式：模拟播放状态
        isPlaying = true;
        isPaused = false; // 清除暂停状态
        playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_pause, 0, 0); // 使用暂停图标
        Toast.makeText(this, "演示模式：模拟音频播放", Toast.LENGTH_SHORT).show();
        
        // 3秒后自动"停止"
        playPauseButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    // 播放完毕，重置状态
                    isPlaying = false;
                    isPaused = false; // 清除暂停状态
                    playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0);
                }
            }
        }, 3000);
    }
    
    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            isPaused = true; // 设置暂停状态
            playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0);
        }
    }
    
    private void resumeAudio() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.start();
                isPlaying = true;
                isPaused = false; // 清除暂停状态
                playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_pause, 0, 0);
            } catch (Exception e) {
                Log.e(TAG, "恢复播放失败", e);
                // 如果恢复失败，重新开始播放
                playAudio();
            }
        }
    }
    
    private void stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
        }
        isPlaying = false;
        playPauseButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0);
    }
    
    private void startRecording() {
        if (!isRecording) {
            isRecording = true;
            recordButton.setPressed(true);
            Toast.makeText(this, getString(R.string.recording_hint), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            recordButton.setPressed(false);
            Toast.makeText(this, getString(R.string.recording_complete), Toast.LENGTH_SHORT).show();
            
            // 模拟评分结果
            int score = 85; // 模拟得分
            showScoreResult(score);
            
            // 标记练习完成并更新进度
            if (exerciseManager != null && currentExercise != null) {
                exerciseManager.markExerciseCompleted(currentExercise.getExerciseId(), score);
            }
        }
    }
    
    private void showScoreResult(int score) {
        scoreTextView.setText(score + "分");
        scoreLayout.setVisibility(View.VISIBLE);
        
        // 显示练习完成后的操作按钮
        if (exerciseCompletionActions != null) {
            exerciseCompletionActions.setVisibility(View.VISIBLE);
            
            // 根据练习状态和重试权限设置按钮状态
            if (exerciseManager != null && currentExercise != null) {
                // 设置重试按钮状态
                if (btnRetryExercise != null) {
                    boolean canRetry = exerciseManager.canRetryExercise();
                    btnRetryExercise.setEnabled(canRetry);
                    btnRetryExercise.setVisibility(canRetry ? View.VISIBLE : View.GONE);
                }
                
                // 设置继续下一个按钮状态
                if (btnContinueNext != null) {
                    boolean hasNext = exerciseManager.hasNextExercise();
                    btnContinueNext.setEnabled(hasNext);
                    btnContinueNext.setText(hasNext ? "继续下一个" : "完成课程");
                }
            }
        }
        
        // 更新导航UI
        updateNavigationUI();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        // 处理ActionBar返回按钮点击
        onBackPressed();
        return true;
    }
    
    @Override
    public void onBackPressed() {
        // 停止音频播放和录音
        super.onBackPressed();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            stopAudio();
        }
        if (isRecording) {
            stopRecording();
        }
        
        // 智能返回导航
        handleBackNavigation();
    }
    
    /**
     * 处理返回导航逻辑
     */
    private void handleBackNavigation() {
        if (isSubCourse && "SubCourseActivity".equals(fromActivity)) {
            // 从子课程练习返回到子课程列表
            Intent intent = getIntent();
            String mainCourseId = intent.getStringExtra("main_course_id");
            String mainCourseTitle = intent.getStringExtra("main_course_title");
            
            Intent backIntent = new Intent(this, SubCourseActivity.class);
            backIntent.putExtra("main_course_id", mainCourseId);
            backIntent.putExtra("main_course_title", mainCourseTitle);
            backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(backIntent);
            finish();
        } else {
            // 默认返回逻辑
            super.onBackPressed();
        }
    }
    
    /**
     * 从子课程ID获取主课程ID
     * 通过Lesson对象的parentCourseId字段获取
     */
    private String getMainCourseIdFromSubCourse(String subCourseId) {
        try {
            LessonManager lessonManager = LessonManager.getInstance(this);
            LessonManager.Lesson subCourse = lessonManager.getLesson(subCourseId);
            
            if (subCourse != null && subCourse.getParentCourseId() != null) {
                return subCourse.getParentCourseId();
            } else {
                Log.w(TAG, "无法获取子课程的父课程ID: " + subCourseId);
                return subCourseId; // 如果无法获取父课程ID，返回原ID
            }
        } catch (Exception e) {
            Log.e(TAG, "获取父课程ID时发生异常: " + subCourseId, e);
            return subCourseId; // 异常情况下返回原ID
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}