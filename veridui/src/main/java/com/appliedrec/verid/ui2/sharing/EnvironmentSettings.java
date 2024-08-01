package com.appliedrec.verid.ui2.sharing;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(EnvironmentSettingsJsonAdapter.class)
public class EnvironmentSettings {

    private final float confidenceThreshold;
    private final int faceDetectorVersion;
    private final float faceTemplateExtractionThreshold;
    private final float authenticationThreshold;
    private final String veridVersion;
    private final String applicationId;
    private final String applicationVersion;
    private final String deviceModel;
    private final String os;

    public EnvironmentSettings(int faceDetectorVersion, float confidenceThreshold, float faceTemplateExtractionThreshold, float authenticationThreshold, String veridVersion, String applicationId, String applicationVersion, String deviceModel, String os) {
        this.faceDetectorVersion = faceDetectorVersion;
        this.confidenceThreshold = confidenceThreshold;
        this.faceTemplateExtractionThreshold = faceTemplateExtractionThreshold;
        this.authenticationThreshold = authenticationThreshold;
        this.veridVersion = veridVersion;
        this.applicationId = applicationId;
        this.applicationVersion = applicationVersion;
        this.deviceModel = deviceModel;
        this.os = os;
    }

    public int getFaceDetectorVersion() {
        return faceDetectorVersion;
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

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getOs() {
        return os;
    }
}
