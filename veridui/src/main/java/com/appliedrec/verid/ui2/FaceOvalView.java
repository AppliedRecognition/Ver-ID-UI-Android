package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.core2.EulerAngle;

public class FaceOvalView extends View {

    private final float lineWidthMultiplier = 0.038f;
    private int strokeColour = Color.BLACK;
    private Float arrowAngle;
    private Float arrowDistance;
    private boolean isStrokeVisible = false;
    private final Path arrowPath = new Path();
    private final Paint ovalPaint = new Paint();
    private final Paint arrowPaint = new Paint();

    public FaceOvalView(Context context) {
        super(context);
        init();
    }

    public FaceOvalView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOvalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ovalPaint.setStyle(Paint.Style.STROKE);
        ovalPaint.setColor(strokeColour);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setColor(Color.WHITE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @UiThread
    public void setStrokeColour(int strokeColour) {
        this.strokeColour = strokeColour;
        invalidate();
    }

    @UiThread
    public int getStrokeColour() {
        return strokeColour;
    }

    public float getLineWidth() {
        return (float) this.getWidth() * this.lineWidthMultiplier;
    }

    @UiThread
    public void setStrokeVisible(boolean visible) {
        if (visible == this.isStrokeVisible) {
            return;
        }
        this.isStrokeVisible = visible;
        this.invalidate();
    }

    @UiThread
    public void drawArrow(float angle, float distance) {
        this.arrowAngle = angle;
        this.arrowDistance = distance;
        this.invalidate();
    }

    @UiThread
    public void removeArrow() {
        this.arrowAngle = null;
        this.arrowDistance = null;
        this.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isStrokeVisible) {
            ovalPaint.setStrokeWidth(getLineWidth());
            canvas.drawOval(0, 0, getWidth(), getHeight(), ovalPaint);
        }
        if (arrowAngle != null && arrowDistance != null) {
            arrowPaint.setStrokeWidth(getLineWidth());
            float arrowLength = (float) getWidth() / 5f;
            float stemLength = Math.min(Math.max(arrowLength * arrowDistance, arrowLength * 0.75f), arrowLength * 1.7f);
            float arrowTipAngle = (float) Math.toRadians(40);
            float arrowTipX = (float) getWidth() / 2 + (float) Math.cos(arrowAngle) * arrowLength / 2f;
            float arrowTipY = (float) getHeight() / 2 + (float) Math.sin(arrowAngle) * arrowLength / 2f;
            float arrowPoint1X = arrowTipX + (float) Math.cos(arrowAngle + Math.PI - arrowTipAngle) * arrowLength * 0.6f;
            float arrowPoint1Y = arrowTipY + (float) Math.sin(arrowAngle + Math.PI - arrowTipAngle) * arrowLength * 0.6f;
            float arrowPoint2X = arrowTipX + (float) Math.cos(arrowAngle + Math.PI + arrowTipAngle) * arrowLength * 0.6f;
            float arrowPoint2Y = arrowTipY + (float) Math.sin(arrowAngle + Math.PI + arrowTipAngle) * arrowLength * 0.6f;
            float arrowStartX = arrowTipX + (float) Math.cos(arrowAngle + Math.PI) * stemLength;
            float arrowStartY = arrowTipY + (float) Math.sin(arrowAngle + Math.PI) * stemLength;
            arrowPath.reset();
            arrowPath.moveTo(arrowPoint1X, arrowPoint1Y);
            arrowPath.lineTo(arrowTipX, arrowTipY);
            arrowPath.lineTo(arrowPoint2X, arrowPoint2Y);
            arrowPath.moveTo(arrowTipX, arrowTipY);
            arrowPath.lineTo(arrowStartX, arrowStartY);
            canvas.drawPath(arrowPath, arrowPaint);
        }
    }
}
