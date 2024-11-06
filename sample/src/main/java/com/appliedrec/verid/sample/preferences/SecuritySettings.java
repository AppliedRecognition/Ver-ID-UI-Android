package com.appliedrec.verid.sample.preferences;

import androidx.annotation.Nullable;

public class SecuritySettings {

    private final int poseCount;
    private final float yawThreshold;
    private final float pitchThreshold;
    private final float authThreshold;
    private final boolean passiveLivenessEnabled;

    SecuritySettings(int poseCount, float yawThreshold, float pitchThreshold, float authThreshold, boolean passiveLivenessEnabled) {
        this.poseCount = poseCount;
        this.yawThreshold = yawThreshold;
        this.pitchThreshold = pitchThreshold;
        this.authThreshold = authThreshold;
        this.passiveLivenessEnabled = passiveLivenessEnabled;
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

    public boolean isPassiveLivenessEnabled() {
        return passiveLivenessEnabled;
    }

    public final static SecuritySettings LOW = new SecuritySettings(1, 15, 10, 3.5f, true);
    public final static SecuritySettings NORMAL = new SecuritySettings(2, 18, 12, 4, true);
    public final static SecuritySettings HIGH = new SecuritySettings(2, 21, 15, 4.5f, true);

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof SecuritySettings) {
            SecuritySettings other = (SecuritySettings) obj;
            return other.poseCount == poseCount && other.yawThreshold == yawThreshold && other.pitchThreshold == pitchThreshold && other.authThreshold == authThreshold && other.passiveLivenessEnabled == passiveLivenessEnabled;
        } else {
            return false;
        }
    }
}
