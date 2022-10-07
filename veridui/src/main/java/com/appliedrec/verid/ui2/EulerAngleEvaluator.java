package com.appliedrec.verid.ui2;

import android.animation.TypeEvaluator;

import com.appliedrec.verid.core2.EulerAngle;

public class EulerAngleEvaluator implements TypeEvaluator<EulerAngle> {

    @Override
    public EulerAngle evaluate(float fraction, EulerAngle startValue, EulerAngle endValue) {
        EulerAngle interpolated = new EulerAngle();
        interpolated.setYaw(startValue.getYaw() + (endValue.getYaw() - startValue.getYaw()) * fraction);
        interpolated.setPitch(startValue.getPitch() + (endValue.getPitch() - startValue.getPitch()) * fraction);
        interpolated.setRoll(startValue.getRoll() + (endValue.getRoll() - startValue.getRoll()) * fraction);
        return interpolated;
    }
}
