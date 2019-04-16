package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.RectF;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import com.appliedrec.verid.core.VerIDSessionSettings;

public class FaceBoundsWidthPreference extends FaceGuidePreference {

    public FaceBoundsWidthPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FaceBoundsWidthPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FaceBoundsWidthPreference(Context context) {
        super(context);
    }

    @Override
    public int getDialogLayoutResource() {
        return R.layout.fragment_face_guide_portrait;
    }

    @Override
    protected int getDefaultValue() {
        return (int)(new VerIDSessionSettings().getFaceBoundsFraction().x * 20f);
    }

    @Override
    protected RectF createFaceRect(float progress, int viewWidth, int viewHeight) {
        float width = (float)viewWidth * progress;
        float height = width / 4f * 5f;
        float x = (float)viewWidth / 2f - width / 2f;
        float y = (float)viewHeight / 2f - height / 2f;
        return new RectF(x, y, x+width, y+height);
    }

    @Override
    protected String getSummaryFromValue(int value) {
        return (value*5)+"% of view width";
    }
}
