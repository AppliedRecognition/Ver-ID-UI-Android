package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.appliedrec.verid.core.VerIDSessionSettings;

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
    public int getDialogLayoutResource() {
        return R.layout.fragment_face_guide_landscape;
    }

    @Override
    protected int getDefaultValue() {
        return (int)(new VerIDSessionSettings().getFaceBoundsFraction().y * 20f);
    }

    @Override
    protected RectF createFaceRect(float progress, int viewWidth, int viewHeight) {
        float height = (float)viewHeight * progress;
        float width = height / 5f * 4f;
        float x = (float)viewWidth / 2f - width / 2f;
        float y = (float)viewHeight / 2f - height / 2f;
        return new RectF(x, y, x+width, y+height);
    }

    @Override
    protected String getSummaryFromValue(int value) {
        return (value*5)+"% of view height";
    }
}
