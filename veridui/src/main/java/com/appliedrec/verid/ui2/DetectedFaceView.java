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

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * View that renders an overlay based on a detected face
 */
@Keep
public class DetectedFaceView extends View {

    private Paint strokePaint;
//    private Paint strokeBackgroundPaint;
    private Paint faceTemplatePaint;
    private Paint landmarkPaint;
    private RectF faceRect;
    private RectF templateRect;
    private final RectF viewRect = new RectF();
    private Double angle;
    private Double distance;
    private PointF[] landmarks;
    private final Path landmarkPath = new Path();
    private final Path path = new Path();
    private final Path arrowPath = new Path();
    private final Path templatePath = new Path();
    private float screenDensity;

    @Keep
    public DetectedFaceView(Context context) {
        super(context);
        init(context);
    }

    @Keep
    public DetectedFaceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);

        int paintFlag = 0;//Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Paint.ANTI_ALIAS_FLAG : 0;

        screenDensity = context.getResources().getDisplayMetrics().density;
        strokePaint = new Paint(paintFlag);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(screenDensity * 8f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

//        strokeBackgroundPaint = new Paint(paintFlag);
//        strokeBackgroundPaint.setStyle(Paint.Style.STROKE);
//        strokeBackgroundPaint.setStrokeWidth(screenDensity * 9f);
//        strokeBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
//        strokeBackgroundPaint.setColor(Color.DKGRAY);

        faceTemplatePaint = new Paint(paintFlag);
        faceTemplatePaint.setStyle(Paint.Style.FILL);
        faceTemplatePaint.setColor(0x80000000);

        templatePath.setFillType(Path.FillType.EVEN_ODD);

        landmarkPaint = new Paint(paintFlag);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setColor(Color.CYAN);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        path.rewind();
        arrowPath.rewind();
        templatePath.rewind();
        landmarkPath.rewind();
        if (templateRect != null) {
            viewRect.right = getWidth();
            viewRect.top = getHeight();
            templatePath.addRect(viewRect, Path.Direction.CCW);
            templatePath.addOval(templateRect, Path.Direction.CCW);
            canvas.drawPath(templatePath, faceTemplatePaint);
        }
        float landmarkRadius;
        if (faceRect != null) {
            landmarkRadius = faceRect.width() * 0.01f;
            strokePaint.setStrokeWidth(faceRect.width() * 0.038f);
//            strokeBackgroundPaint.setStrokeWidth(strokePaint.getStrokeWidth()+screenDensity*2);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                strokePaint.setShadowLayer(15, 0, 0, Color.argb(0x33, 0, 0, 0));
//            }
            path.addOval(faceRect, Path.Direction.CW);
            canvas.drawPath(path, strokePaint);
            if (angle != null && distance != null) {
                drawArrow(canvas, angle, distance);
            }
        } else {
            landmarkRadius = 6f;
        }
        if (landmarks != null && landmarks.length > 0) {
            for (PointF point : landmarks) {
                landmarkPath.moveTo(point.x, point.y);
                landmarkPath.addCircle(point.x, point.y, landmarkRadius, Path.Direction.CW);
            }
            canvas.drawPath(landmarkPath, landmarkPaint);
        }
    }

    /**
     * Set face landmarks
     * @param landmarks Face landmarks to be rendered in the next draw
     */
    @Keep
    @UiThread
    public void setFaceLandmarks(PointF[] landmarks) {
        this.landmarks = landmarks;
        invalidate();
    }

    /**
     * Set face rectangle
     * @param faceRect Bounding box around the detected face or where the face is expected – rendered as oval with coloured stroke
     * @param templateRect Bounding box around the detected face – rendered as a cutout in the background colour
     * @param faceRectColour Colour of the stroke around the face
     * @param faceBackgroundColour Background colour in which the face will be "cut out"
     * @param angle Angle of the arrow that indicates the desired pose
     * @param distance Distance from the desired pose – used to determine the length of the arrow
     */
    @Keep
    @UiThread
    public void setFaceRect(@Nullable RectF faceRect, @Nullable RectF templateRect, int faceRectColour, int faceBackgroundColour, @Nullable Double angle, @Nullable Double distance) {
        this.faceRect = faceRect;
        this.templateRect = templateRect;
        this.strokePaint.setColor(faceRectColour);
        this.faceTemplatePaint.setColor(faceBackgroundColour);
        this.angle = angle;
        this.distance = distance;
        invalidate();
    }

    private void drawArrow(Canvas canvas, double angle, double distance) {
        double arrowLength = faceRect.width() / 5;
        distance *= 1.7;
        double arrowStemLength = Math.min(Math.max(arrowLength * distance, arrowLength * 0.75), arrowLength * 1.7);
        double arrowAngle = Math.toRadians(40);
        float arrowTipX = (float)(faceRect.centerX() + Math.cos(angle) * arrowLength / 2);
        float arrowTipY = (float)(faceRect.centerY() + Math.sin(angle) * arrowLength / 2);
        float arrowPoint1X = (float)(arrowTipX + Math.cos(angle + Math.PI - arrowAngle) * arrowLength * 0.6);
        float arrowPoint1Y = (float)(arrowTipY + Math.sin(angle + Math.PI - arrowAngle) * arrowLength * 0.6);
        float arrowPoint2X = (float)(arrowTipX + Math.cos(angle + Math.PI + arrowAngle) * arrowLength * 0.6);
        float arrowPoint2Y = (float)(arrowTipY + Math.sin(angle + Math.PI + arrowAngle) * arrowLength * 0.6);
        float arrowStartX = (float)(arrowTipX + Math.cos(angle + Math.PI) * arrowStemLength);
        float arrowStartY = (float)(arrowTipY + Math.sin(angle + Math.PI) * arrowStemLength);


        arrowPath.moveTo(arrowPoint1X, arrowPoint1Y);
        arrowPath.lineTo(arrowTipX, arrowTipY);
        arrowPath.lineTo(arrowPoint2X, arrowPoint2Y);
        arrowPath.moveTo(arrowTipX, arrowTipY);
        arrowPath.lineTo(arrowStartX, arrowStartY);

//        canvas.drawPath(arrowPath, strokeBackgroundPaint);
        canvas.drawPath(arrowPath, strokePaint);
    }
}
