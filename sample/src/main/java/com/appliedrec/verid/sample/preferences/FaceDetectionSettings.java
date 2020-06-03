package com.appliedrec.verid.sample.preferences;

import androidx.annotation.Nullable;

public class FaceDetectionSettings {

    private final float confidenceThreshold;
    private final float faceTemplateExtractionThreshold;

    FaceDetectionSettings(float confidenceThreshold, float faceTemplateExtractionThreshold) {
        this.confidenceThreshold = confidenceThreshold;
        this.faceTemplateExtractionThreshold = faceTemplateExtractionThreshold;
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public float getFaceTemplateExtractionThreshold() {
        return faceTemplateExtractionThreshold;
    }

    public static final FaceDetectionSettings PERMISSIVE = new FaceDetectionSettings(-0.5f, 6.0f);
    public static final FaceDetectionSettings NORMAL = new FaceDetectionSettings(-0.5f, 8.0f);
    public static final FaceDetectionSettings RESTRICTIVE = new FaceDetectionSettings(0, 9.0f);

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof FaceDetectionSettings) {
            FaceDetectionSettings other = (FaceDetectionSettings) obj;
            return other.confidenceThreshold == confidenceThreshold && other.faceTemplateExtractionThreshold == faceTemplateExtractionThreshold;
        } else {
            return false;
        }
    }
}
