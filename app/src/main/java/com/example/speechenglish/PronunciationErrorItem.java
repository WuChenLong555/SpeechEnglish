package com.example.speechenglish;

/**
 * 发音错误项数据类
 * 用于在RecyclerView中显示发音错误信息
 */
public class PronunciationErrorItem {
    private String errorType;
    private String expectedPhoneme;
    private String actualPhoneme;
    private float confidence;
    private float timeStart;
    private float timeEnd;
    private String description;
    
    public PronunciationErrorItem(String errorType, String expectedPhoneme, String actualPhoneme,
                                 float confidence, float timeStart, float timeEnd, String description) {
        this.errorType = errorType;
        this.expectedPhoneme = expectedPhoneme;
        this.actualPhoneme = actualPhoneme;
        this.confidence = confidence;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.description = description;
    }
    
    // Getters
    public String getErrorType() {
        return errorType;
    }
    
    public String getExpectedPhoneme() {
        return expectedPhoneme;
    }
    
    public String getActualPhoneme() {
        return actualPhoneme;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public float getTimeStart() {
        return timeStart;
    }
    
    public float getTimeEnd() {
        return timeEnd;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取错误严重程度
     * @return 0-轻微, 1-中等, 2-严重
     */
    public int getSeverityLevel() {
        if (confidence > 0.8f) {
            return 0; // 轻微
        } else if (confidence > 0.5f) {
            return 1; // 中等
        } else {
            return 2; // 严重
        }
    }
    
    /**
     * 获取时间范围字符串
     * @return 格式化的时间范围
     */
    public String getTimeRange() {
        return String.format("%.2fs - %.2fs", timeStart, timeEnd);
    }
    
    /**
     * 判断是否为连读相关错误
     * @return true如果是连读错误
     */
    public boolean isLiaisonError() {
        return errorType.contains("LINKING") || 
               errorType.contains("NASALIZATION") || 
               errorType.contains("H_DROPPING") || 
               errorType.contains("PLOSIVE_ELISION") || 
               errorType.contains("SAME_CONSONANT");
    }
    
    /**
     * 判断是否为弱读相关错误
     * @return true如果是弱读错误
     */
    public boolean isWeakFormError() {
        return errorType.contains("WEAK");
    }
}