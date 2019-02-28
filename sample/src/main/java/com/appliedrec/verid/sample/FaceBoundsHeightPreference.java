package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.RectF;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import com.appliedrec.verid.core.SessionSettings;

public class FaceBoundsHeightPreference extends FaceGuidePreference {

    public FaceBoundsHeightPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FaceBoundsHeightPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FaceBoundsHeightPreference(Context context) {
        super(context);
    }

    @Override
    protected ConstraintLayout createView() {
        return (ConstraintLayout)((LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.fragment_face_guide_landscape, null);
    }

    @Override
    protected int getDefaultValue() {
        return (int)(new SessionSettings().getFaceBoundsFraction().y * 20f);
    }

    @Override
    protected RectF createFaceRect(float progress, int viewWidth, int viewHeight) {
        float height = (float)viewHeight * progress;
        float width = height / 5f * 4f;
        float x = (float)viewWidth / 2f - width / 2f;
        float y = (float)viewHeight / 2f - height / 2f;
        return new RectF(x, y, x+width, y+height);
    }
}
