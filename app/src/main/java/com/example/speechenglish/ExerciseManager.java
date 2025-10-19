package com.example.speechenglish;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 练习管理器类
 * 负责管理多练习流程、练习切换和进度跟踪
 */
public class ExerciseManager {
    private static final String TAG = "ExerciseManager";
    
    private List<LessonManager.Exercise> exercises;
    private int currentExerciseIndex;
    private CourseProgress courseProgress;
    private String courseId;
    private Context context;
    private ProgressPersistenceManager persistenceManager;
    
    // 练习状态监听器接口
    public interface ExerciseStateListener {
        void onExerciseChanged(LessonManager.Exercise exercise);
        void onExerciseCompleted(String exerciseId, int score);
        void onCourseCompleted(String courseId);
        void onProgressUpdated(CourseProgress courseProgress);
    }
    
    private ExerciseStateListener stateListener;

    public ExerciseManager(Context context, String courseId) {
        this.context = context;
        this.courseId = courseId;
        this.exercises = new ArrayList<>();
        this.currentExerciseIndex = 0;
        this.persistenceManager = ProgressPersistenceManager.getInstance(context);
        
        // 尝试加载已保存的课程进度
        loadCourseProgress();
    }

    /**
     * 加载课程进度
     */
    private void loadCourseProgress() {
        CourseProgress savedProgress = persistenceManager.loadCourseProgress(courseId);
        if (savedProgress != null) {
            this.courseProgress = savedProgress;
            Log.i(TAG, "已加载课程进度: " + courseId);
        } else {
            this.courseProgress = new CourseProgress(courseId);
            Log.i(TAG, "创建新的课程进度: " + courseId);
        }
    }

    /**
     * 保存课程进度
     */
    private void saveCourseProgress() {
        if (courseProgress != null) {
            persistenceManager.saveCourseProgress(courseProgress);
            Log.d(TAG, "课程进度已保存: " + courseId);
        }
    }

    /**
     * 初始化练习列表
     * @param exercises 练习列表
     */
    public void initializeExercises(List<LessonManager.Exercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            Log.w(TAG, "练习列表为空");
            return;
        }
        
        this.exercises = new ArrayList<>(exercises);
        
        // 如果课程进度为空或练习数量不匹配，重新初始化
        if (courseProgress.getExerciseProgresses().isEmpty() || 
            courseProgress.getExerciseProgresses().size() != exercises.size()) {
            courseProgress.initializeExercises(exercises);
            
            // 加载已保存的单个练习进度
            Map<String, ExerciseProgress> savedProgresses = persistenceManager.loadAllExerciseProgress(courseId);
            for (Map.Entry<String, ExerciseProgress> entry : savedProgresses.entrySet()) {
                courseProgress.updateExerciseProgress(entry.getKey(), entry.getValue().getScore());
            }
        }
        
        // 设置当前练习索引为第一个未完成的练习
        findNextIncompleteExercise();
        
        Log.i(TAG, "初始化练习管理器，练习数量: " + exercises.size() + ", 当前索引: " + currentExerciseIndex);
        
        // 通知状态变化
        notifyExerciseChanged();
        notifyNavigationStateChanged();
    }

    /**
     * 查找下一个未完成的练习
     */
    private void findNextIncompleteExercise() {
        for (int i = 0; i < exercises.size(); i++) {
            LessonManager.Exercise exercise = exercises.get(i);
            ExerciseProgress progress = courseProgress.getExerciseProgress(exercise.getExerciseId());
            if (progress == null || !progress.isCompleted()) {
                currentExerciseIndex = i;
                return;
            }
        }
        // 如果所有练习都完成了，设置为最后一个练习
        currentExerciseIndex = Math.max(0, exercises.size() - 1);
    }

    /**
     * 获取当前练习
     * @return 当前练习，如果没有练习则返回null
     */
    public LessonManager.Exercise getCurrentExercise() {
        if (exercises.isEmpty() || currentExerciseIndex < 0 || currentExerciseIndex >= exercises.size()) {
            return null;
        }
        return exercises.get(currentExerciseIndex);
    }

    /**
     * 获取下一个练习
     * @return 下一个练习，如果没有则返回null
     */
    public LessonManager.Exercise getNextExercise() {
        if (!hasNextExercise()) {
            return null;
        }
        return exercises.get(currentExerciseIndex + 1);
    }

    /**
     * 获取上一个练习
     * @return 上一个练习，如果没有则返回null
     */
    public LessonManager.Exercise getPreviousExercise() {
        if (!hasPreviousExercise()) {
            return null;
        }
        return exercises.get(currentExerciseIndex - 1);
    }

    /**
     * 切换到下一个练习
     * @return 是否成功切换
     */
    public boolean moveToNextExercise() {
        if (!hasNextExercise()) {
            return false;
        }
        
        currentExerciseIndex++;
        Log.d(TAG, "切换到下一个练习: " + (currentExerciseIndex + 1) + "/" + exercises.size());
        
        notifyExerciseChanged();
        notifyNavigationStateChanged();
        
        return true;
    }

    /**
     * 切换到上一个练习
     * @return 是否成功切换
     */
    public boolean moveToPreviousExercise() {
        if (!hasPreviousExercise()) {
            return false;
        }
        
        currentExerciseIndex--;
        Log.d(TAG, "切换到上一个练习: " + (currentExerciseIndex + 1) + "/" + exercises.size());
        
        notifyExerciseChanged();
        notifyNavigationStateChanged();
        
        return true;
    }

    /**
     * 跳转到指定练习
     * @param index 练习索引
     * @return 是否成功跳转
     */
    public boolean moveToExercise(int index) {
        if (index < 0 || index >= exercises.size()) {
            return false;
        }
        
        currentExerciseIndex = index;
        Log.d(TAG, "跳转到练习: " + (currentExerciseIndex + 1) + "/" + exercises.size());
        
        notifyExerciseChanged();
        notifyNavigationStateChanged();
        
        return true;
    }

    /**
     * 检查是否有下一个练习
     * @return 是否有下一个练习
     */
    public boolean hasNextExercise() {
        return currentExerciseIndex < exercises.size() - 1;
    }

    /**
     * 检查是否有上一个练习
     * @return 是否有上一个练习
     */
    public boolean hasPreviousExercise() {
        return currentExerciseIndex > 0;
    }

    /**
     * 获取总练习数量
     * @return 总练习数量
     */
    public int getTotalExerciseCount() {
        return exercises.size();
    }

    /**
     * 获取当前练习编号（从1开始）
     * @return 当前练习编号
     */
    public int getCurrentExerciseNumber() {
        return currentExerciseIndex + 1;
    }

    /**
     * 获取当前练习索引（从0开始）
     * @return 当前练习索引
     */
    public int getCurrentExerciseIndex() {
        return currentExerciseIndex;
    }

    /**
     * 标记当前练习为已完成
     * @param score 练习得分
     */
    public void completeCurrentExercise(int score) {
        LessonManager.Exercise currentExercise = getCurrentExercise();
        if (currentExercise == null) {
            Log.w(TAG, "当前没有练习可以标记为完成");
            return;
        }
        
        // 更新练习进度
        courseProgress.updateExerciseProgress(currentExercise.getExerciseId(), score);
        
        // 保存单个练习进度
        ExerciseProgress exerciseProgress = courseProgress.getExerciseProgress(currentExercise.getExerciseId());
        if (exerciseProgress != null) {
            persistenceManager.saveExerciseProgress(courseId, exerciseProgress);
        }
        
        // 保存课程进度
        saveCourseProgress();
        
        Log.i(TAG, "练习完成: " + currentExercise.getTitle() + ", 得分: " + score);
        
        // 通知练习完成
        if (stateListener != null) {
            stateListener.onExerciseCompleted(currentExercise.getExerciseId(), score);
        }
        
        // 检查课程是否完成
        if (courseProgress.isCourseCompleted()) {
            Log.i(TAG, "课程完成: " + courseId);
            if (stateListener != null) {
                stateListener.onCourseCompleted(courseId);
            }
        }
    }

    /**
     * 获取课程进度
     * @return 课程进度对象
     */
    public CourseProgress getCourseProgress() {
        return courseProgress;
    }

    /**
     * 获取当前练习的进度
     * @return 当前练习进度，如果没有当前练习则返回null
     */
    public ExerciseProgress getCurrentExerciseProgress() {
        LessonManager.Exercise currentExercise = getCurrentExercise();
        if (currentExercise == null) {
            return null;
        }
        return courseProgress.getExerciseProgress(currentExercise.getExerciseId());
    }

    /**
     * 检查当前练习是否已完成
     * @return 是否已完成
     */
    public boolean isCurrentExerciseCompleted() {
        ExerciseProgress progress = getCurrentExerciseProgress();
        return progress != null && progress.isCompleted();
    }

    /**
     * 设置状态监听器
     * @param listener 状态监听器
     */
    public void setStateListener(ExerciseStateListener listener) {
        this.stateListener = listener;
    }

    /**
     * 重置课程进度
     */
    public void resetCourseProgress() {
        courseProgress.reset();
        
        // 删除持久化数据
        persistenceManager.deleteCourseProgress(courseId);
        
        Log.i(TAG, "课程进度已重置: " + courseId);
    }

    /**
     * 重置当前练习进度
     */
    public void resetCurrentExerciseProgress() {
        LessonManager.Exercise currentExercise = getCurrentExercise();
        if (currentExercise == null) {
            Log.w(TAG, "当前没有练习可以重置");
            return;
        }
        
        // 重置练习进度
        ExerciseProgress progress = courseProgress.getExerciseProgress(currentExercise.getExerciseId());
        progress.reset();
        
        // 删除持久化数据
        persistenceManager.deleteExerciseProgress(courseId, currentExercise.getExerciseId());
        
        // 保存课程进度
        saveCourseProgress();
        
        Log.i(TAG, "练习进度已重置: " + currentExercise.getTitle());
    }

    /**
     * 手动保存进度（用于定期保存或应用退出时保存）
     */
    public void saveProgress() {
        saveCourseProgress();
        
        // 保存所有练习进度
        for (ExerciseProgress progress : courseProgress.getExerciseProgresses().values()) {
            persistenceManager.saveExerciseProgress(courseId, progress);
        }
        
        Log.d(TAG, "所有进度已保存: " + courseId);
    }

    /**
     * 获取练习列表
     * @return 练习列表的副本
     */
    public List<LessonManager.Exercise> getExercises() {
        return new ArrayList<>(exercises);
    }

    /**
     * 通知练习变化
     */
    private void notifyExerciseChanged() {
        if (stateListener != null) {
            LessonManager.Exercise currentExercise = getCurrentExercise();
            if (currentExercise != null) {
                stateListener.onExerciseChanged(currentExercise);
            }
        }
    }

    /**
     * 通知导航状态变化
     */
    private void notifyNavigationStateChanged() {
        // 这个方法暂时不需要实现，因为接口已经简化
    }

    /**
     * 构造函数 - 兼容旧版本调用
     */
    public ExerciseManager(String courseId, List<LessonManager.Exercise> exercises, Context context) {
        this(context, courseId);
        initializeExercises(exercises);
    }

    /**
     * 设置练习状态监听器
     * @param listener 监听器
     */
    public void setExerciseStateListener(ExerciseStateListener listener) {
        this.stateListener = listener;
    }

    /**
     * 前往上一个练习
     */
    public void goToPreviousExercise() {
        moveToPreviousExercise();
    }

    /**
     * 前往下一个练习
     */
    public void goToNextExercise() {
        moveToNextExercise();
    }

    /**
     * 标记练习完成
     * @param exerciseId 练习ID
     * @param score 得分
     */
    public void markExerciseCompleted(String exerciseId, int score) {
        // 更新练习进度
        courseProgress.updateExerciseProgress(exerciseId, score);
        
        // 保存单个练习进度
        ExerciseProgress exerciseProgress = courseProgress.getExerciseProgress(exerciseId);
        if (exerciseProgress != null) {
            persistenceManager.saveExerciseProgress(courseId, exerciseProgress);
        }
        
        // 保存课程进度
        saveCourseProgress();
        
        Log.i(TAG, "练习完成: " + exerciseId + ", 得分: " + score);
        
        // 通知练习完成
        if (stateListener != null) {
            stateListener.onExerciseCompleted(exerciseId, score);
        }
        
        // 通知进度更新
        if (stateListener != null) {
            stateListener.onProgressUpdated(courseProgress);
        }
        
        // 检查课程是否完成
        if (courseProgress.isCourseCompleted()) {
            Log.i(TAG, "课程完成: " + courseId);
            if (stateListener != null) {
                stateListener.onCourseCompleted(courseId);
            }
        }
    }

    /**
     * 检查当前练习是否允许重试
     * @return 是否允许重试
     */
    public boolean canRetryExercise() {
        LessonManager.Exercise currentExercise = getCurrentExercise();
        if (currentExercise == null) {
            return false;
        }
        
        ExerciseProgress progress = courseProgress.getExerciseProgress(currentExercise.getExerciseId());
        return progress != null && progress.isRetryAllowed();
    }

    /**
     * 重新练习当前练习（不完全重置，保留最佳成绩）
     */
    public void retryExercise() {
        LessonManager.Exercise currentExercise = getCurrentExercise();
        if (currentExercise == null) {
            Log.w(TAG, "当前没有练习可以重试");
            return;
        }
        
        ExerciseProgress progress = courseProgress.getExerciseProgress(currentExercise.getExerciseId());
        if (progress != null && progress.isRetryAllowed()) {
            // 重置当前得分和完成状态，但保留最佳成绩和尝试次数
            progress.setCompleted(false);
            progress.setScore(0);
            progress.setCompletedTime(0);
            
            // 保存进度
            persistenceManager.saveExerciseProgress(courseId, progress);
            saveCourseProgress();
            
            Log.i(TAG, "重新练习: " + currentExercise.getTitle());
            
            // 通知状态变化
            if (stateListener != null) {
                stateListener.onExerciseChanged(currentExercise);
                stateListener.onProgressUpdated(courseProgress);
            }
        }
    }

    /**
     * 检查指定练习是否已完成
     * @param exerciseId 练习ID
     * @return 是否已完成
     */
    public boolean isExerciseCompleted(String exerciseId) {
        ExerciseProgress progress = courseProgress.getExerciseProgress(exerciseId);
        return progress != null && progress.isCompleted();
    }

    /**
     * 完全重置练习进度（包括最佳成绩）
     * @param exerciseId 练习ID
     */
    public void resetExerciseProgress(String exerciseId) {
        ExerciseProgress progress = courseProgress.getExerciseProgress(exerciseId);
        if (progress != null) {
            progress.reset();
            
            // 保存进度
            persistenceManager.saveExerciseProgress(courseId, progress);
            saveCourseProgress();
            
            Log.i(TAG, "完全重置练习进度: " + exerciseId);
            
            // 通知进度更新
            if (stateListener != null) {
                stateListener.onProgressUpdated(courseProgress);
            }
        }
    }
}