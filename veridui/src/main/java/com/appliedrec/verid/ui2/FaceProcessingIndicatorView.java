package com.appliedrec.verid.ui2;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.core2.Face;

import java.util.Arrays;

public class FaceProcessingIndicatorView extends View implements ValueAnimator.AnimatorUpdateListener {

    private static class CoordinateHolder {

        float x = 0f;
        float y = 0f;
        Path path = new Path();
        float dotRadius = 8f;

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
            updatePath();
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
            updatePath();
        }

        public void setDotRadius(float radius) {
            dotRadius = radius;
            updatePath();
        }

        private void updatePath() {
            path.reset();
            path.addOval(x-dotRadius, y-dotRadius, x+dotRadius, y+dotRadius, Path.Direction.CW);
        }

        public Path getPath() {
            return path;
        }
    }

    private final Path landmarkPath = new Path();
    private Face face;
    private final Paint landmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix landmarkMatrix = new Matrix();
    private ObjectAnimator animator;
    private CoordinateHolder coordinates = new CoordinateHolder();
    private float dotRadius = 8f;

    public FaceProcessingIndicatorView(Context context) {
        this(context, null);
    }

    public FaceProcessingIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setColor(Color.WHITE);
//        landmarkPaint.setStrokeJoin(Paint.Join.ROUND);
//        landmarkPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @UiThread
    public void setFace(@NonNull Face face) {
        this.face = face;
        updateLandmarkPath();
    }

    private void animateLandmarkPath() {
        if (landmarkPath.isEmpty()) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ObjectAnimator.ofFloat(coordinates, "x", "y", landmarkPath);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setDuration(1000);
        animator.addUpdateListener(this);
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(coordinates.getPath(), landmarkPaint);
        Log.d("Ver-ID", String.format("Drew dot at x: %.0f, y: %.0f", coordinates.getX(), coordinates.getY()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateLandmarkPath();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void updateLandmarkPath() {
        if (getWidth() == 0 || getHeight() == 0 || face == null) {
            return;
        }
        float scale = Math.max((float)this.getWidth() / face.getBounds().width(), (float)this.getHeight() / face.getBounds().height());
        landmarkMatrix.reset();
        landmarkMatrix.setTranslate(0-face.getBounds().left, 0-face.getBounds().top);
        landmarkMatrix.postScale(scale, scale);
        PointF[] landmarks = Arrays.stream(face.getLandmarks()).map(pt -> {
            float[] point = {pt.x, pt.y};
            landmarkMatrix.mapPoints(point);
            return new PointF(point[0], point[1]);
        }).toArray(PointF[]::new);
        int[] startPoints = {0,17,22,27,31,36,42,48,60};
        int[] closePoints = {41,47,59,67};
        landmarkPath.reset();
        for (int i=17; i<landmarks.length; i++) {
            PointF point = landmarks[i];
            int ptIndex = i;
            if (Arrays.stream(startPoints).anyMatch(index -> index == ptIndex)) {
                landmarkPath.moveTo(point.x, point.y);
            } else {
                landmarkPath.lineTo(point.x, point.y);
            }
            if (Arrays.stream(closePoints).anyMatch(index -> index == ptIndex)) {
                landmarkPath.close();
            }
        }
        dotRadius = (float)getWidth() / 100f;
//        landmarkPaint.setStrokeWidth((float)getWidth() / 60f);
        animateLandmarkPath();
    }

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
        invalidate();
    }
}
