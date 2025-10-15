package com.example.speechenglish;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * 课程管理器测试Activity
 */
public class LessonTestActivity extends Activity {
    private static final String TAG = "LessonTestActivity";
    private TextView resultTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson_test);
        
        resultTextView = findViewById(R.id.result_text);
        
        // 测试课程管理器
        testLessonManager();
    }
    
    private void testLessonManager() {
        try {
            LessonManager lessonManager = LessonManager.getInstance(this);
            lessonManager.initialize();
            
            StringBuilder result = new StringBuilder();
            result.append("课程管理器测试结果:\n\n");
            
            // 获取所有课程
            var lessons = lessonManager.getAllLessons();
            result.append("课程数量: ").append(lessons.size()).append("\n\n");
            
            for (LessonManager.Lesson lesson : lessons) {
                result.append("课程ID: ").append(lesson.getLessonId()).append("\n");
                result.append("标题: ").append(lesson.getTitle()).append("\n");
                result.append("描述: ").append(lesson.getDescription()).append("\n");
                result.append("难度: ").append(lesson.getDifficulty()).append("\n");
                result.append("分类: ").append(lesson.getCategory()).append("\n");
                result.append("时长: ").append(lesson.getEstimatedDuration()).append("分钟\n");
                result.append("---\n");
            }
            
            resultTextView.setText(result.toString());
            Log.i(TAG, "课程管理器测试完成");
            
        } catch (Exception e) {
            String errorMsg = "课程管理器测试失败: " + e.getMessage();
            resultTextView.setText(errorMsg);
            Log.e(TAG, errorMsg, e);
        }
    }
}