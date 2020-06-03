package com.appliedrec.verid.sample.preferences;

import androidx.annotation.Nullable;

public class SecuritySettings {

    private final int poseCount;
    private final float yawThreshold;
    private final float pitchThreshold;
    private final float authThreshold;

    SecuritySettings(int poseCount, float yawThreshold, float pitchThreshold, float authThreshold) {
        this.poseCount = poseCount;
        this.yawThreshold = yawThreshold;
        this.pitchThreshold = pitchThreshold;
        this.authThreshold = authThreshold;
    }

    public int getPoseCount() {
        return poseCount;
    }

    public float getYawThreshold() {
        return yawThreshold;
    }

    public float getPitchThreshold() {
        return pitchThreshold;
    }

    public float getAuthThreshold() {
        return authThreshold;
    }

    public final static SecuritySettings LOW = new SecuritySettings(1, 12, 10, 3.5f);
    public final static SecuritySettings NORMAL = new SecuritySettings(2, 15, 12, 4);
    public final static SecuritySettings HIGH = new SecuritySettings(3, 18, 15, 4.5f);

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof SecuritySettings) {
            SecuritySettings other = (SecuritySettings) obj;
            return other.poseCount == poseCount && other.yawThreshold == yawThreshold && other.pitchThreshold == pitchThreshold && other.authThreshold == authThreshold;
        } else {
            return false;
        }
    }
}
