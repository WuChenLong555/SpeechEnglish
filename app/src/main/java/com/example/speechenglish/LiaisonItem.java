package com.example.speechenglish;

public class LiaisonItem {
    public final String transition;
    public final float timePoint;
    public final float confidence;

    public LiaisonItem(String transition, float timePoint, float confidence) {
        this.transition = transition;
        this.timePoint = timePoint;
        this.confidence = confidence;
    }
} 