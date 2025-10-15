package com.example.speechenglish;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程管理器类 - 负责加载和管理所有课程数据
 */
public class LessonManager {
    private static final String TAG = "LessonManager";
    private static LessonManager instance;
    
    private Context context;
    private Map<String, Lesson> lessonsMap;
    private List<Lesson> lessonsList;
    private boolean isInitialized = false;
    
    // 课程列表配置文件路径
    private static final String LESSONS_LIST_FILE = "lessons_list.json";
    
    private LessonManager(Context context) {
        this.context = context.getApplicationContext();
        this.lessonsMap = new HashMap<>();
        this.lessonsList = new ArrayList<>();
    }
    
    public static synchronized LessonManager getInstance(Context context) {
        if (instance == null) {
            instance = new LessonManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化课程管理器，加载所有课程数据
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }
        
        try {
            loadLessonsList();
            isInitialized = true;
            Log.i(TAG, "课程管理器初始化完成，加载了 " + lessonsList.size() + " 个课程");
        } catch (Exception e) {
            Log.e(TAG, "课程管理器初始化失败", e);
        }
    }
    
    /**
     * 加载课程列表配置文件
     */
    private void loadLessonsList() throws Exception {
        AssetManager assetManager = context.getAssets();
        
        try (InputStream inputStream = assetManager.open(LESSONS_LIST_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            
            // 直接解析JSON数组（lessons_list.json是一个数组）
            JSONArray lessonsArray = new JSONArray(jsonContent.toString());
            
            for (int i = 0; i < lessonsArray.length(); i++) {
                JSONObject lessonJson = lessonsArray.getJSONObject(i);
                Lesson lesson = parseLessonInfo(lessonJson);
                
                if (lesson != null) {
                    lessonsMap.put(lesson.getLessonId(), lesson);
                    lessonsList.add(lesson);
                }
            }
        }
    }
    
    /**
     * 解析课程信息
     */
    private Lesson parseLessonInfo(JSONObject lessonJson) {
        try {
            String lessonId = lessonJson.getString("id");
            String title = lessonJson.getString("title");
            String description = lessonJson.optString("description", "");
            String category = lessonJson.optString("category", "");
            String difficulty = lessonJson.optString("difficulty", "beginner");
            String filePath = lessonJson.optString("file", "");
            String type = lessonJson.optString("type", "lesson"); // 默认为普通课程
            
            // 解析时长（从字符串中提取数字）
            String durationStr = lessonJson.optString("duration", "15分钟");
            int estimatedDuration = 15; // 默认值
            try {
                // 从"15分钟"这样的字符串中提取数字
                String[] parts = durationStr.split("分钟");
                if (parts.length > 0) {
                    estimatedDuration = Integer.parseInt(parts[0].trim());
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "解析时长失败: " + durationStr + ", 使用默认值15分钟");
            }
            
            Lesson lesson = new Lesson(lessonId, title, description, category, difficulty, estimatedDuration, filePath);
            lesson.setType(type);
            
            return lesson;
        } catch (JSONException e) {
            Log.e(TAG, "解析课程信息失败: " + lessonJson.toString(), e);
            return null;
        }
    }
    
    /**
     * 加载指定课程的详细数据（包括练习列表或子课程列表）
     */
    public Lesson loadLessonDetails(String lessonId) throws Exception {
        Lesson lesson = lessonsMap.get(lessonId);
        if (lesson == null) {
            throw new IllegalArgumentException("课程不存在: " + lessonId);
        }
        
        // 如果已经加载过详细数据，直接返回
        if (lesson.isDetailsLoaded()) {
            return lesson;
        }
        
        // 获取课程文件路径，如果未指定则使用默认路径
        String filePath = lesson.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            filePath = "lessons/" + lessonId + ".json";
        }
        
        AssetManager assetManager = context.getAssets();
        
        try (InputStream inputStream = assetManager.open(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            
            JSONObject lessonDataJson = new JSONObject(jsonContent.toString());
            
            // 检查课程类型
            if (lesson.isMainCourse()) {
                // 主课程：解析子课程列表
                List<Lesson> subCourses = parseSubCourses(lessonDataJson);
                lesson.setSubCourses(subCourses);
                Log.i(TAG, "加载主课程详细数据完成: " + lessonId + ", 子课程数量: " + subCourses.size());
            } else {
                // 普通课程：解析练习数据
                List<Exercise> exercises = parseExercises(lessonDataJson, lessonId);
                lesson.setExercises(exercises);
                Log.i(TAG, "加载课程详细数据完成: " + lessonId + ", 练习数量: " + exercises.size());
            }
            
            lesson.setDetailsLoaded(true);
            
        } catch (Exception e) {
            Log.e(TAG, "加载课程详细数据失败: " + lessonId + ", 文件路径: " + filePath, e);
            throw e;
        }
        
        return lesson;
    }
    
    /**
     * 解析练习数据
     */
    private List<Exercise> parseExercises(JSONObject lessonDataJson, String lessonId) throws JSONException {
        List<Exercise> exercises = new ArrayList<>();
        
        // 从JSON中获取exercises数组
        JSONArray exercisesArray = lessonDataJson.getJSONArray("exercises");
        
        for (int i = 0; i < exercisesArray.length(); i++) {
            JSONObject exerciseJson = exercisesArray.getJSONObject(i);
            
            String exerciseId = exerciseJson.getString("id");
            String title = exerciseJson.getString("title");
            String description = exerciseJson.optString("description", "");
            String targetPhoneme = exerciseJson.getString("target_phoneme");
            JSONArray exampleWordsArray = exerciseJson.getJSONArray("example_words");
            
            // 将示例单词数组转换为字符串
            StringBuilder exampleWordsBuilder = new StringBuilder();
            for (int j = 0; j < exampleWordsArray.length(); j++) {
                if (j > 0) exampleWordsBuilder.append(", ");
                exampleWordsBuilder.append(exampleWordsArray.getString(j));
            }
            String exampleWords = exampleWordsBuilder.toString();
            
            Exercise exercise = new Exercise(
                exerciseId,
                title,
                targetPhoneme,
                exampleWords,
                "" // 音频路径暂时为空
            );
            
            exercises.add(exercise);
        }
        
        return exercises;
    }
    
    /**
     * 解析子课程数据
     */
    private List<Lesson> parseSubCourses(JSONObject lessonDataJson) throws JSONException {
        List<Lesson> subCourses = new ArrayList<>();
        
        // 从JSON中获取sub_courses数组
        JSONArray subCoursesArray = lessonDataJson.getJSONArray("sub_courses");
        
        for (int i = 0; i < subCoursesArray.length(); i++) {
            JSONObject subCourseJson = subCoursesArray.getJSONObject(i);
            
            String subCourseId = subCourseJson.getString("id");
            String title = subCourseJson.getString("title");
            String description = subCourseJson.optString("description", "");
            String category = subCourseJson.optString("category", "");
            String difficulty = subCourseJson.optString("difficulty", "beginner");
            String filePath = subCourseJson.optString("file", "");
            
            // 解析时长
            String durationStr = subCourseJson.optString("duration", "15分钟");
            int estimatedDuration = 15;
            try {
                String[] parts = durationStr.split("分钟");
                if (parts.length > 0) {
                    estimatedDuration = Integer.parseInt(parts[0].trim());
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "解析子课程时长失败: " + durationStr + ", 使用默认值15分钟");
            }
            
            Lesson subCourse = new Lesson(subCourseId, title, description, category, difficulty, estimatedDuration, filePath);
            subCourse.setType("lesson"); // 子课程默认为普通课程类型
            subCourses.add(subCourse);
        }
        
        return subCourses;
    }
    
    /**
     * 获取所有课程列表（基本信息）
     */
    public List<Lesson> getAllLessons() {
        return new ArrayList<>(lessonsList);
    }
    
    /**
     * 根据课程ID获取课程
     */
    public Lesson getLesson(String lessonId) {
        return lessonsMap.get(lessonId);
    }
    
    /**
     * 获取指定分类的课程
     */
    public List<Lesson> getLessonsByCategory(String category) {
        List<Lesson> result = new ArrayList<>();
        for (Lesson lesson : lessonsList) {
            if (category.equals(lesson.getCategory())) {
                result.add(lesson);
            }
        }
        return result;
    }
    
    /**
     * 获取指定难度的课程
     */
    public List<Lesson> getLessonsByDifficulty(String difficulty) {
        List<Lesson> result = new ArrayList<>();
        for (Lesson lesson : lessonsList) {
            if (difficulty.equals(lesson.getDifficulty())) {
                result.add(lesson);
            }
        }
        return result;
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * 课程数据类
     */
    public static class Lesson {
        private String lessonId;
        private String title;
        private String description;
        private String category;
        private String difficulty;
        private int estimatedDuration;
        private String filePath;
        private String type; // 课程类型：lesson（普通课程）或 main_course（主课程）
        private List<Exercise> exercises;
        private List<Lesson> subCourses; // 子课程列表（仅主课程使用）
        private boolean detailsLoaded = false;
        
        public Lesson(String lessonId, String title, String description, 
                     String category, String difficulty, int estimatedDuration) {
            this(lessonId, title, description, category, difficulty, estimatedDuration, "");
        }
        
        public Lesson(String lessonId, String title, String description, 
                     String category, String difficulty, int estimatedDuration, String filePath) {
            this.lessonId = lessonId;
            this.title = title;
            this.description = description;
            this.category = category;
            this.difficulty = difficulty;
            this.estimatedDuration = estimatedDuration;
            this.filePath = filePath;
            this.type = "lesson"; // 默认为普通课程
            this.exercises = new ArrayList<>();
            this.subCourses = new ArrayList<>();
        }
        
        // Getter 方法
        public String getLessonId() { return lessonId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getDifficulty() { return difficulty; }
        public int getEstimatedDuration() { return estimatedDuration; }
        public String getFilePath() { return filePath; }
        public String getType() { return type; }
        public List<Exercise> getExercises() { return exercises; }
        public List<Lesson> getSubCourses() { return subCourses; }
        public boolean isDetailsLoaded() { return detailsLoaded; }
        
        // 检查是否是主课程
        public boolean isMainCourse() {
            return "main_course".equals(type);
        }
        
        // Setter 方法
        public void setExercises(List<Exercise> exercises) { this.exercises = exercises; }
        public void setSubCourses(List<Lesson> subCourses) { this.subCourses = subCourses; }
        public void setType(String type) { this.type = type; }
        public void setDetailsLoaded(boolean detailsLoaded) { this.detailsLoaded = detailsLoaded; }
    }
    
    /**
     * 练习数据类
     */
    public static class Exercise {
        private String exerciseId;
        private String title;
        private String targetPhoneme;
        private String exampleWords;
        private String audioPath;
        
        public Exercise(String exerciseId, String title, String targetPhoneme, 
                       String exampleWords, String audioPath) {
            this.exerciseId = exerciseId;
            this.title = title;
            this.targetPhoneme = targetPhoneme;
            this.exampleWords = exampleWords;
            this.audioPath = audioPath;
        }
        
        // Getter 方法
        public String getExerciseId() { return exerciseId; }
        public String getTitle() { return title; }
        public String getTargetPhoneme() { return targetPhoneme; }
        public String getExampleWords() { return exampleWords; }
        public String getAudioPath() { return audioPath; }
    }
}