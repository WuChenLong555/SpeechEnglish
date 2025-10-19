package com.example.speechenglish;

/**
 * 练习进度数据类
 * 用于跟踪单个练习的完成状态和得分
 */
public class ExerciseProgress {
    private String exerciseId;
    private boolean completed;
    private int score;
    private long completedTime;
    private int bestScore;  // 最佳成绩
    private boolean canRetry;  // 是否允许重复练习
    private int attemptCount;  // 尝试次数

    public ExerciseProgress() {
        // 默认构造函数
        this.canRetry = true;  // 默认允许重复练习
        this.attemptCount = 0;
    }

    public ExerciseProgress(String exerciseId) {
        this.exerciseId = exerciseId;
        this.completed = false;
        this.score = 0;
        this.completedTime = 0;
        this.bestScore = 0;
        this.canRetry = true;  // 默认允许重复练习
        this.attemptCount = 0;
    }

    public ExerciseProgress(String exerciseId, boolean completed, int score, long completedTime) {
        this.exerciseId = exerciseId;
        this.completed = completed;
        this.score = score;
        this.completedTime = completedTime;
        this.bestScore = score;
        this.canRetry = true;  // 默认允许重复练习
        this.attemptCount = completed ? 1 : 0;
    }

    // Getter 方法
    public String getExerciseId() {
        return exerciseId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getScore() {
        return score;
    }

    public long getCompletedTime() {
        return completedTime;
    }

    public int getBestScore() {
        return bestScore;
    }

    public boolean isCanRetry() {
        return canRetry;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    // Setter 方法
    public void setExerciseId(String exerciseId) {
        this.exerciseId = exerciseId;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setCompletedTime(long completedTime) {
        this.completedTime = completedTime;
    }

    public void setBestScore(int bestScore) {
        this.bestScore = bestScore;
    }

    public void setCanRetry(boolean canRetry) {
        this.canRetry = canRetry;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    /**
     * 标记练习为已完成
     * @param score 练习得分
     */
    public void markCompleted(int score) {
        this.completed = true;
        this.score = score;
        this.completedTime = System.currentTimeMillis();
        this.attemptCount++;
        
        // 更新最佳成绩
        if (score > this.bestScore) {
            this.bestScore = score;
        }
    }

    /**
     * 更新练习得分（不标记为完成）
     * @param score 新的得分
     */
    public void updateScore(int score) {
        this.score = score;
        this.attemptCount++;
        
        // 更新最佳成绩
        if (score > this.bestScore) {
            this.bestScore = score;
        }
    }

    /**
     * 检查是否允许重试
     * @return 是否允许重试
     */
    public boolean isRetryAllowed() {
        return this.canRetry;
    }

    /**
     * 重置练习进度
     */
    public void reset() {
        this.completed = false;
        this.score = 0;
        this.completedTime = 0;
        this.bestScore = 0;
        this.attemptCount = 0;
        this.canRetry = true;
    }

    @Override
    public String toString() {
        return "ExerciseProgress{" +
                "exerciseId='" + exerciseId + '\'' +
                ", completed=" + completed +
                ", score=" + score +
                ", completedTime=" + completedTime +
                '}';
    }
}