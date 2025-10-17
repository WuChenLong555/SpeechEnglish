package com.speech.english.phoneme;

/**
 * 音素错误信息
 */
public class PhonemeError {
    private String type;
    private int position;
    private String expectedPhoneme;
    private String actualPhoneme;
    private float confidence;
    private float timeStart;
    private float timeEnd;

    public PhonemeError(String type, int position, String expectedPhoneme, 
                        String actualPhoneme, float confidence, 
                        float timeStart, float timeEnd) {
        this.type = type;
        this.position = position;
        this.expectedPhoneme = expectedPhoneme;
        this.actualPhoneme = actualPhoneme;
        this.confidence = confidence;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
    }

    public String getType() {
        return type;
    }

    public int getPosition() {
        return position;
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
}