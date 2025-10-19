package com.example.speechenglish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程进度数据类
 * 用于管理课程中所有练习的进度状态
 */
public class CourseProgress {
    private String courseId;
    private Map<String, ExerciseProgress> exerciseProgresses;
    private boolean courseCompleted;
    private float overallScore;
    private int currentExerciseIndex;  // 当前练习索引

    public CourseProgress() {
        this.exerciseProgresses = new HashMap<>();
        this.currentExerciseIndex = 0;
    }

    public CourseProgress(String courseId) {
        this.courseId = courseId;
        this.exerciseProgresses = new HashMap<>();
        this.courseCompleted = false;
        this.overallScore = 0.0f;
        this.currentExerciseIndex = 0;
    }

    // Getter 方法
    public String getCourseId() {
        return courseId;
    }

    public Map<String, ExerciseProgress> getExerciseProgresses() {
        return exerciseProgresses;
    }

    public boolean isCourseCompleted() {
        return courseCompleted;
    }

    public float getOverallScore() {
        return overallScore;
    }

    public int getCurrentExerciseIndex() {
        return currentExerciseIndex;
    }

    // Setter 方法
    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public void setExerciseProgresses(Map<String, ExerciseProgress> exerciseProgresses) {
        this.exerciseProgresses = exerciseProgresses;
    }

    public void setCourseCompleted(boolean courseCompleted) {
        this.courseCompleted = courseCompleted;
    }

    public void setOverallScore(float overallScore) {
        this.overallScore = overallScore;
    }

    public void setCurrentExerciseIndex(int currentExerciseIndex) {
        this.currentExerciseIndex = currentExerciseIndex;
    }

    /**
     * 检查是否可以导航到上一个练习
     * @return 是否可以导航到上一个练习
     */
    public boolean canNavigateToPrevious() {
        return currentExerciseIndex > 0;
    }

    /**
     * 检查是否可以导航到下一个练习
     * @return 是否可以导航到下一个练习
     */
    public boolean canNavigateToNext() {
        return currentExerciseIndex < getTotalExerciseCount() - 1;
    }

    /**
     * 获取指定练习的进度
     * @param exerciseId 练习ID
     * @return 练习进度，如果不存在则返回新的进度对象
     */
    public ExerciseProgress getExerciseProgress(String exerciseId) {
        ExerciseProgress progress = exerciseProgresses.get(exerciseId);
        if (progress == null) {
            progress = new ExerciseProgress(exerciseId);
            exerciseProgresses.put(exerciseId, progress);
        }
        return progress;
    }

    /**
     * 更新练习进度
     * @param exerciseId 练习ID
     * @param score 练习得分
     */
    public void updateExerciseProgress(String exerciseId, int score) {
        ExerciseProgress progress = getExerciseProgress(exerciseId);
        progress.markCompleted(score);
        
        // 更新整体进度
        updateOverallProgress();
    }

    /**
     * 获取已完成的练习数量
     * @return 已完成练习数量
     */
    public int getCompletedExerciseCount() {
        int count = 0;
        for (ExerciseProgress progress : exerciseProgresses.values()) {
            if (progress.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取总练习数量
     * @return 总练习数量
     */
    public int getTotalExerciseCount() {
        return exerciseProgresses.size();
    }

    /**
     * 获取课程完成百分比
     * @return 完成百分比 (0-100)
     */
    public float getCompletionPercentage() {
        if (exerciseProgresses.isEmpty()) {
            return 0.0f;
        }
        return (float) getCompletedExerciseCount() / getTotalExerciseCount() * 100;
    }

    /**
     * 初始化课程中的所有练习
     * @param exercises 练习列表
     */
    public void initializeExercises(List<LessonManager.Exercise> exercises) {
        for (LessonManager.Exercise exercise : exercises) {
            if (!exerciseProgresses.containsKey(exercise.getExerciseId())) {
                exerciseProgresses.put(exercise.getExerciseId(), new ExerciseProgress(exercise.getExerciseId()));
            }
        }
        updateOverallProgress();
    }

    /**
     * 更新整体进度和得分
     */
    private void updateOverallProgress() {
        if (exerciseProgresses.isEmpty()) {
            courseCompleted = false;
            overallScore = 0.0f;
            return;
        }

        int completedCount = 0;
        int totalScore = 0;
        int completedExercises = 0;

        for (ExerciseProgress progress : exerciseProgresses.values()) {
            if (progress.isCompleted()) {
                completedCount++;
                totalScore += progress.getScore();
                completedExercises++;
            }
        }

        // 检查课程是否完成
        courseCompleted = (completedCount == exerciseProgresses.size());

        // 计算平均得分
        if (completedExercises > 0) {
            overallScore = (float) totalScore / completedExercises;
        } else {
            overallScore = 0.0f;
        }
    }

    /**
     * 重置课程进度
     */
    public void reset() {
        for (ExerciseProgress progress : exerciseProgresses.values()) {
            progress.reset();
        }
        courseCompleted = false;
        overallScore = 0.0f;
        currentExerciseIndex = 0;
    }

    /**
     * 获取已完成练习的列表
     * @return 已完成练习进度列表
     */
    public List<ExerciseProgress> getCompletedExercises() {
        List<ExerciseProgress> completed = new ArrayList<>();
        for (ExerciseProgress progress : exerciseProgresses.values()) {
            if (progress.isCompleted()) {
                completed.add(progress);
            }
        }
        return completed;
    }

    @Override
    public String toString() {
        return "CourseProgress{" +
                "courseId='" + courseId + '\'' +
                ", exerciseCount=" + exerciseProgresses.size() +
                ", completedCount=" + getCompletedExerciseCount() +
                ", courseCompleted=" + courseCompleted +
                ", overallScore=" + overallScore +
                '}';
    }
}