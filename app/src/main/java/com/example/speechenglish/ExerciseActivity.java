package com.example.speechenglish;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 练习界面 - 占位符实现，后续需要完善
 */
public class ExerciseActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise);
        
        // 获取传递的课程信息
        Intent intent = getIntent();
        String lessonId = intent.getStringExtra("lesson_id");
        String lessonTitle = intent.getStringExtra("lesson_title");
        
        // 显示课程信息
        TextView titleTextView = findViewById(R.id.lesson_title);
        titleTextView.setText(lessonTitle);
        
        Toast.makeText(this, "进入课程: " + lessonTitle, Toast.LENGTH_SHORT).show();
    }
}