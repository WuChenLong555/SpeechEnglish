package com.example.speechenglish;

public class PhonemeAlignmentItem {
    public final int phonemeId;
    public final String phoneme;
    public final float score;
    public final float timePoint;

    public PhonemeAlignmentItem(int phonemeId, String phoneme, float score, float timePoint) {
        this.phonemeId = phonemeId;
        this.phoneme = phoneme;
        this.score = score;
        this.timePoint = timePoint;
    }
} 