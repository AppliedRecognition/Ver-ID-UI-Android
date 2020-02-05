package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Matrix;
import androidx.collection.ArraySet;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;

public class TransformableRelativeLayout extends RelativeLayout {

    private Matrix transformationMatrix = new Matrix();
    private final ArraySet<View> transformableViews = new ArraySet<>();

    public TransformableRelativeLayout(Context context) {
        super(context);
        init();
    }

    public TransformableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setStaticTransformationsEnabled(true);
    }

    public void addTransformableView(View view) {
        transformableViews.add(view);
        addView(view);
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        transformableViews.remove(view);
    }

    public void setTransformationMatrix(Matrix matrix) {
        transformationMatrix = matrix;
        for (View view : transformableViews) {
            view.invalidate();
        }
    }

    @Override
    protected boolean getChildStaticTransformation(View child, Transformation t) {
        if (transformableViews.contains(child)) {
            t.getMatrix().set(transformationMatrix);
            return true;
        }
        return super.getChildStaticTransformation(child, t);
    }
}
