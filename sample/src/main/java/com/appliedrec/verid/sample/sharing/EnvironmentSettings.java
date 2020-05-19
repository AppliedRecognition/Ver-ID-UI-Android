package com.appliedrec.verid.sample.sharing;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(EnvironmentSettingsJsonAdapter.class)
public class EnvironmentSettings {

    private final float confidenceThreshold;
    private final float faceTemplateExtractionThreshold;
    private final float authenticationThreshold;
    private final String veridVersion;

    public EnvironmentSettings(float confidenceThreshold, float faceTemplateExtractionThreshold, float authenticationThreshold, String veridVersion) {
        this.confidenceThreshold = confidenceThreshold;
        this.faceTemplateExtractionThreshold = faceTemplateExtractionThreshold;
        this.authenticationThreshold = authenticationThreshold;
        this.veridVersion = veridVersion;
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public float getFaceTemplateExtractionThreshold() {
        return faceTemplateExtractionThreshold;
    }

    public float getAuthenticationThreshold() {
        return authenticationThreshold;
    }

    public String getVeridVersion() {
        return veridVersion;
    }
}
