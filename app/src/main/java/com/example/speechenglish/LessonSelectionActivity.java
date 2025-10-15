package com.example.speechenglish;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 课程选择界面 - 显示所有可用的课程供用户选择
 */
public class LessonSelectionActivity extends AppCompatActivity {
    private static final String TAG = "LessonSelectionActivity";
    
    private LessonManager lessonManager;
    private RecyclerView lessonsRecyclerView;
    private LessonAdapter lessonAdapter;
    private ProgressBar loadingProgressBar;
    private TextView emptyTextView;
    private List<LessonManager.Lesson> lessons;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson_selection);
        
        initializeViews();
        initializeLessonManager();
    }
    
    private void initializeViews() {
        lessonsRecyclerView = findViewById(R.id.lessons_recycler_view);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        emptyTextView = findViewById(R.id.empty_text_view);
        
        // 设置RecyclerView
        lessonsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 初始化适配器
        lessonAdapter = new LessonAdapter(new ArrayList<>());
        lessonsRecyclerView.setAdapter(lessonAdapter);
        
        // 设置点击监听器
        lessonAdapter.setOnLessonClickListener(this::onLessonSelected);
    }
    
    private void initializeLessonManager() {
        lessonManager = LessonManager.getInstance(this);
        
        // 如果尚未初始化，则初始化课程管理器
        if (!lessonManager.isInitialized()) {
            showLoading(true);
            
            new Thread(() -> {
                try {
                    lessonManager.initialize();
                    
                    runOnUiThread(() -> {
                        showLoading(false);
                        loadLessons();
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "课程管理器初始化失败", e);
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("课程数据加载失败，请重试");
                    });
                }
            }).start();
        } else {
            // 如果已经初始化，直接加载课程
            loadLessons();
        }
    }
    
    private void loadLessons() {
        lessons = lessonManager.getAllLessons();
        
        if (lessons.isEmpty()) {
            showEmptyState(true);
            emptyTextView.setText("暂无可用课程");
        } else {
            showEmptyState(false);
            lessonAdapter.updateLessons(lessons);
            Log.i(TAG, "加载了 " + lessons.size() + " 个课程");
        }
    }
    
    private void onLessonSelected(LessonManager.Lesson lesson) {
        Log.i(TAG, "用户选择了课程: " + lesson.getTitle());
        
        // 检查是否是主课程
        if (lesson.isMainCourse()) {
            // 主课程：加载子课程列表
            showLoading(true);
            
            new Thread(() -> {
                try {
                    LessonManager.Lesson detailedLesson = lessonManager.loadLessonDetails(lesson.getLessonId());
                    
                    runOnUiThread(() -> {
                        showLoading(false);
                        
                        // 显示子课程选择界面
                        showSubCourses(detailedLesson);
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "加载主课程详细数据失败: " + lesson.getLessonId(), e);
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("课程数据加载失败，请重试");
                    });
                }
            }).start();
        } else {
            // 普通课程：直接进入练习界面
            showLoading(true);
            
            new Thread(() -> {
                try {
                    LessonManager.Lesson detailedLesson = lessonManager.loadLessonDetails(lesson.getLessonId());
                    
                    runOnUiThread(() -> {
                        showLoading(false);
                        
                        // 启动练习界面
                        Intent intent = new Intent(LessonSelectionActivity.this, ExerciseActivity.class);
                        intent.putExtra("lesson_id", detailedLesson.getLessonId());
                        intent.putExtra("lesson_title", detailedLesson.getTitle());
                        startActivity(intent);
                        
                        // 添加进入动画
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "加载课程详细数据失败: " + lesson.getLessonId(), e);
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("课程数据加载失败，请重试");
                    });
                }
            }).start();
        }
    }
    
    /**
     * 显示子课程选择界面
     */
    private void showSubCourses(LessonManager.Lesson mainCourse) {
        List<LessonManager.Lesson> subCourses = mainCourse.getSubCourses();
        
        if (subCourses.isEmpty()) {
            showError("该主课程暂无子课程");
            return;
        }
        
        // 更新界面显示子课程
        lessonAdapter.updateLessons(subCourses);
        
        // 更新标题栏显示主课程名称
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(mainCourse.getTitle());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // 修改点击监听器，让子课程点击进入练习界面
        lessonAdapter.setOnLessonClickListener(subCourse -> {
            // 子课程点击：直接进入练习界面
            showLoading(true);
            
            new Thread(() -> {
                try {
                    LessonManager.Lesson detailedSubCourse = lessonManager.loadLessonDetails(subCourse.getLessonId());
                    
                    runOnUiThread(() -> {
                        showLoading(false);
                        
                        // 启动练习界面
                        Intent intent = new Intent(LessonSelectionActivity.this, ExerciseActivity.class);
                        intent.putExtra("lesson_id", detailedSubCourse.getLessonId());
                        intent.putExtra("lesson_title", detailedSubCourse.getTitle());
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
        });
    }
    
    private void showLoading(boolean show) {
        loadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        lessonsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(View.GONE);
    }
    
    private void showEmptyState(boolean show) {
        emptyTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        lessonsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        loadingProgressBar.setVisibility(View.GONE);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showEmptyState(true);
        emptyTextView.setText(message);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新界面状态
        if (lessonManager != null && lessonManager.isInitialized()) {
            loadLessons();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // 检查当前是否在显示子课程列表
            if (isShowingSubCourses()) {
                // 返回主课程列表
                showMainCourseList();
                return true;
            } else {
                finish();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 检查当前是否显示子课程列表
     */
    private boolean isShowingSubCourses() {
        // 检查当前显示的课程列表是否包含子课程
        // 这里可以添加更复杂的逻辑来判断当前状态
        return getSupportActionBar() != null && 
               getSupportActionBar().isShowing() && 
               getSupportActionBar().getTitle() != null &&
               !getSupportActionBar().getTitle().toString().equals("课程选择");
    }
    
    /**
     * 显示主课程列表
     */
    private void showMainCourseList() {
        // 重新加载主课程列表
        loadLessons();
        
        // 恢复标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("课程选择");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        
        // 恢复主课程的点击监听器
        lessonAdapter.setOnLessonClickListener(this::onLessonSelected);
    }
}