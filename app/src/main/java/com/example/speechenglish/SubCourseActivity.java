package com.example.speechenglish;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 子课程选择界面 - 显示指定主课程的所有子课程供用户选择
 */
public class SubCourseActivity extends AppCompatActivity {
    private static final String TAG = "SubCourseActivity";
    
    private LessonManager lessonManager;
    private RecyclerView subCoursesRecyclerView;
    private LessonAdapter subCourseAdapter;
    private ProgressBar loadingProgressBar;
    private TextView emptyTextView;
    private TextView mainCourseTitleView;
    private TextView subCourseCountText;
    private ImageButton backButton;
    
    private String mainCourseId;
    private String mainCourseTitle;
    private List<LessonManager.Lesson> subCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_course);
        
        // 获取传递的参数
        Intent intent = getIntent();
        mainCourseId = intent.getStringExtra("main_course_id");
        mainCourseTitle = intent.getStringExtra("main_course_title");
        
        if (mainCourseId == null) {
            Log.e(TAG, "未提供主课程ID");
            showError("参数错误，无法加载子课程");
            return;
        }
        
        initializeViews();
        initializeLessonManager();
        loadSubCourses();
    }
    
    private void initializeViews() {
        subCoursesRecyclerView = findViewById(R.id.sub_courses_recycler_view);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        emptyTextView = findViewById(R.id.empty_text_view);
        mainCourseTitleView = findViewById(R.id.main_course_title);
        subCourseCountText = findViewById(R.id.sub_course_count_text);
        backButton = findViewById(R.id.back_button);
        
        // 设置标题
        if (mainCourseTitle != null && !mainCourseTitle.isEmpty()) {
            mainCourseTitleView.setText(mainCourseTitle);
        }
        
        // 设置返回按钮点击监听器
        backButton.setOnClickListener(v -> onBackPressed());
        
        // 设置RecyclerView
        subCoursesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 初始化适配器
        subCourseAdapter = new LessonAdapter(new ArrayList<>());
        subCoursesRecyclerView.setAdapter(subCourseAdapter);
        
        // 设置子课程点击监听器
        subCourseAdapter.setOnLessonClickListener(this::onSubCourseSelected);
    }
    
    private void initializeLessonManager() {
        try {
            lessonManager = LessonManager.getInstance(this);
        } catch (Exception e) {
            Log.e(TAG, "初始化LessonManager失败", e);
            showError("初始化失败，请重启应用");
        }
    }
    
    private void loadSubCourses() {
        if (lessonManager == null) {
            showError("课程管理器未初始化");
            return;
        }
        
        showLoading(true);
        
        new Thread(() -> {
            try {
                // 加载主课程详细信息
                LessonManager.Lesson mainCourse = lessonManager.loadLessonDetails(mainCourseId);
                
                if (mainCourse == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("无法加载主课程信息");
                    });
                    return;
                }
                
                subCourses = mainCourse.getSubCourses();
                
                runOnUiThread(() -> {
                    showLoading(false);
                    
                    if (subCourses == null || subCourses.isEmpty()) {
                        showEmptyState(true);
                        emptyTextView.setText("该主课程暂无子课程");
                        updateSubCourseCount(0);
                    } else {
                        subCourseAdapter.updateLessons(subCourses);
                        updateSubCourseCount(subCourses.size());
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "加载子课程失败: " + mainCourseId, e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("加载子课程失败，请重试");
                });
            }
        }).start();
    }
    
    private void onSubCourseSelected(LessonManager.Lesson subCourse) {
        Log.d(TAG, "选择子课程: " + subCourse.getLessonId());
        
        showLoading(true);
        
        new Thread(() -> {
            try {
                // 加载子课程详细数据
                LessonManager.Lesson detailedSubCourse = lessonManager.loadLessonDetails(subCourse.getLessonId());
                
                runOnUiThread(() -> {
                    showLoading(false);
                    
                    // 启动练习界面
                    Intent intent = new Intent(SubCourseActivity.this, ExerciseActivity.class);
                    intent.putExtra("lesson_id", detailedSubCourse.getLessonId());
                    intent.putExtra("lesson_title", detailedSubCourse.getTitle());
                    intent.putExtra("from_activity", "SubCourseActivity");
                    intent.putExtra("is_sub_course", true);
                    intent.putExtra("main_course_id", mainCourseId);
                    intent.putExtra("main_course_title", mainCourseTitle);
                    startActivity(intent);
                    
                    // 添加进入动画
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "加载子课程详细数据失败: " + subCourse.getLessonId(), e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("子课程数据加载失败，请重试");
                });
            }
        }).start();
    }
    
    private void showLoading(boolean show) {
        loadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        subCoursesRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(View.GONE);
    }
    
    private void showEmptyState(boolean show) {
        emptyTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        subCoursesRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        loadingProgressBar.setVisibility(View.GONE);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "错误: " + message);
    }
    
    private void updateSubCourseCount(int count) {
        subCourseCountText.setText(count + "个子课程");
    }
    
    @Override
    public void onBackPressed() {
        // 返回到主课程列表
        Intent intent = new Intent(this, LessonSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        
        // 添加退出动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}