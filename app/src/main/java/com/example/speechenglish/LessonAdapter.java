package com.example.speechenglish;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程列表适配器
 */
public class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.LessonViewHolder> {
    
    private List<LessonManager.Lesson> lessons;
    private OnLessonClickListener onLessonClickListener;
    
    public LessonAdapter(List<LessonManager.Lesson> lessons) {
        this.lessons = new ArrayList<>(lessons);
    }
    
    public void updateLessons(List<LessonManager.Lesson> newLessons) {
        this.lessons.clear();
        this.lessons.addAll(newLessons);
        notifyDataSetChanged();
    }
    
    public void setOnLessonClickListener(OnLessonClickListener listener) {
        this.onLessonClickListener = listener;
    }
    
    @NonNull
    @Override
    public LessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lesson, parent, false);
        return new LessonViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull LessonViewHolder holder, int position) {
        LessonManager.Lesson lesson = lessons.get(position);
        holder.bind(lesson);
        
        holder.itemView.setOnClickListener(v -> {
            if (onLessonClickListener != null) {
                onLessonClickListener.onLessonClick(lesson);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return lessons.size();
    }
    
    static class LessonViewHolder extends RecyclerView.ViewHolder {
        private TextView titleTextView;
        private TextView descriptionTextView;
        private TextView difficultyTag;
        private TextView categoryTag;
        private TextView durationText;
        
        public LessonViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.lesson_title);
            descriptionTextView = itemView.findViewById(R.id.lesson_description);
            difficultyTag = itemView.findViewById(R.id.difficulty_tag);
            categoryTag = itemView.findViewById(R.id.category_tag);
            durationText = itemView.findViewById(R.id.duration_text);
        }
        
        public void bind(LessonManager.Lesson lesson) {
            titleTextView.setText(lesson.getTitle());
            descriptionTextView.setText(lesson.getDescription());
            
            // 设置难度标签
            setDifficultyTag(lesson.getDifficulty());
            
            // 设置分类标签
            categoryTag.setText(lesson.getCategory());
            
            // 设置时长
            durationText.setText("约" + lesson.getEstimatedDuration() + "分钟");
        }
        
        private void setDifficultyTag(String difficulty) {
            switch (difficulty.toLowerCase()) {
                case "beginner":
                    difficultyTag.setText("初级");
                    difficultyTag.setBackgroundResource(R.drawable.difficulty_beginner_background);
                    break;
                case "intermediate":
                    difficultyTag.setText("中级");
                    difficultyTag.setBackgroundResource(R.drawable.difficulty_intermediate_background);
                    break;
                case "advanced":
                    difficultyTag.setText("高级");
                    difficultyTag.setBackgroundResource(R.drawable.difficulty_advanced_background);
                    break;
                default:
                    difficultyTag.setText("初级");
                    difficultyTag.setBackgroundResource(R.drawable.difficulty_beginner_background);
                    break;
            }
        }
    }
    
    public interface OnLessonClickListener {
        void onLessonClick(LessonManager.Lesson lesson);
    }
}