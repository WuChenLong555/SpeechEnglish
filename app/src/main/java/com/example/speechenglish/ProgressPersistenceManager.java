package com.example.speechenglish;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 练习进度持久化管理器
 * 使用SharedPreferences存储和管理练习进度数据
 */
public class ProgressPersistenceManager {
    private static final String TAG = "ProgressPersistenceManager";
    private static final String PREFS_NAME = "exercise_progress";
    private static final String KEY_COURSE_PROGRESS = "course_progress_";
    private static final String KEY_EXERCISE_PROGRESS = "exercise_progress_";
    
    private static ProgressPersistenceManager instance;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    
    private ProgressPersistenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized ProgressPersistenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProgressPersistenceManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 保存课程进度
     */
    public void saveCourseProgress(CourseProgress courseProgress) {
        try {
            String key = KEY_COURSE_PROGRESS + courseProgress.getCourseId();
            String json = gson.toJson(courseProgress);
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(key, json);
            editor.apply();
            
            Log.d(TAG, "课程进度已保存: " + courseProgress.getCourseId());
        } catch (Exception e) {
            Log.e(TAG, "保存课程进度失败: " + courseProgress.getCourseId(), e);
        }
    }
    
    /**
     * 加载课程进度
     */
    public CourseProgress loadCourseProgress(String courseId) {
        try {
            String key = KEY_COURSE_PROGRESS + courseId;
            String json = sharedPreferences.getString(key, null);
            
            if (json != null) {
                CourseProgress courseProgress = gson.fromJson(json, CourseProgress.class);
                Log.d(TAG, "课程进度已加载: " + courseId);
                return courseProgress;
            } else {
                Log.d(TAG, "未找到课程进度数据: " + courseId);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "加载课程进度失败: " + courseId, e);
            return null;
        }
    }
    
    /**
     * 保存单个练习进度
     */
    public void saveExerciseProgress(String courseId, ExerciseProgress exerciseProgress) {
        try {
            String key = KEY_EXERCISE_PROGRESS + courseId + "_" + exerciseProgress.getExerciseId();
            String json = gson.toJson(exerciseProgress);
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(key, json);
            editor.apply();
            
            Log.d(TAG, "练习进度已保存: " + exerciseProgress.getExerciseId());
        } catch (Exception e) {
            Log.e(TAG, "保存练习进度失败: " + exerciseProgress.getExerciseId(), e);
        }
    }
    
    /**
     * 加载单个练习进度
     */
    public ExerciseProgress loadExerciseProgress(String courseId, String exerciseId) {
        try {
            String key = KEY_EXERCISE_PROGRESS + courseId + "_" + exerciseId;
            String json = sharedPreferences.getString(key, null);
            
            if (json != null) {
                ExerciseProgress exerciseProgress = gson.fromJson(json, ExerciseProgress.class);
                Log.d(TAG, "练习进度已加载: " + exerciseId);
                return exerciseProgress;
            } else {
                Log.d(TAG, "未找到练习进度数据: " + exerciseId);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "加载练习进度失败: " + exerciseId, e);
            return null;
        }
    }
    
    /**
     * 加载课程中所有练习的进度
     */
    public Map<String, ExerciseProgress> loadAllExerciseProgress(String courseId) {
        Map<String, ExerciseProgress> progressMap = new HashMap<>();
        
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            String keyPrefix = KEY_EXERCISE_PROGRESS + courseId + "_";
            
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(keyPrefix)) {
                    String json = (String) entry.getValue();
                    if (json != null) {
                        ExerciseProgress progress = gson.fromJson(json, ExerciseProgress.class);
                        progressMap.put(progress.getExerciseId(), progress);
                    }
                }
            }
            
            Log.d(TAG, "已加载课程所有练习进度: " + courseId + ", 数量: " + progressMap.size());
        } catch (Exception e) {
            Log.e(TAG, "加载课程所有练习进度失败: " + courseId, e);
        }
        
        return progressMap;
    }
    
    /**
     * 删除课程进度
     */
    public void deleteCourseProgress(String courseId) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // 删除课程进度
            String courseKey = KEY_COURSE_PROGRESS + courseId;
            editor.remove(courseKey);
            
            // 删除所有相关的练习进度
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            String exerciseKeyPrefix = KEY_EXERCISE_PROGRESS + courseId + "_";
            
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(exerciseKeyPrefix)) {
                    editor.remove(key);
                }
            }
            
            editor.apply();
            Log.d(TAG, "课程进度已删除: " + courseId);
        } catch (Exception e) {
            Log.e(TAG, "删除课程进度失败: " + courseId, e);
        }
    }
    
    /**
     * 删除单个练习进度
     */
    public void deleteExerciseProgress(String courseId, String exerciseId) {
        try {
            String key = KEY_EXERCISE_PROGRESS + courseId + "_" + exerciseId;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.apply();
            
            Log.d(TAG, "练习进度已删除: " + exerciseId);
        } catch (Exception e) {
            Log.e(TAG, "删除练习进度失败: " + exerciseId, e);
        }
    }
    
    /**
     * 重置课程进度
     */
    public void resetCourseProgress(String courseId) {
        deleteCourseProgress(courseId);
        Log.d(TAG, "课程进度已重置: " + courseId);
    }
    
    /**
     * 获取所有已保存的课程ID
     */
    public java.util.Set<String> getAllCourseIds() {
        java.util.Set<String> courseIds = new java.util.HashSet<>();
        
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(KEY_COURSE_PROGRESS)) {
                    String courseId = key.substring(KEY_COURSE_PROGRESS.length());
                    courseIds.add(courseId);
                }
            }
            
            Log.d(TAG, "已找到课程数量: " + courseIds.size());
        } catch (Exception e) {
            Log.e(TAG, "获取所有课程ID失败", e);
        }
        
        return courseIds;
    }
    
    /**
     * 清除所有进度数据
     */
    public void clearAllProgress() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
            
            Log.d(TAG, "所有进度数据已清除");
        } catch (Exception e) {
            Log.e(TAG, "清除所有进度数据失败", e);
        }
    }
    
    /**
     * 获取存储的数据大小（用于调试）
     */
    public int getStoredDataCount() {
        return sharedPreferences.getAll().size();
    }
}