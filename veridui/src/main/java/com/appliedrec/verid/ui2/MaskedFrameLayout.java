package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MaskedFrameLayout extends FrameLayout {

    private Bitmap mask;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Canvas maskCanvas;
    private Canvas tempCanvas;
    private Bitmap canvasBitmap;
    private final PorterDuffXfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    public MaskedFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public MaskedFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        blackPaint.setColor(Color.BLACK);
        blackPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && h != oldh && w > 0 && h > 0) {
            mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            maskCanvas = new Canvas(mask);
            maskCanvas.drawOval(0, 0, w, h, blackPaint);
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            tempCanvas = new Canvas(canvasBitmap);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (tempCanvas == null || mask == null) {
            super.dispatchDraw(canvas);
            return;
        }
        tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        super.dispatchDraw(tempCanvas);
        maskPaint.setXfermode(xfermode);
        tempCanvas.drawBitmap(mask, 0, 0, maskPaint);
        maskPaint.setXfermode(null);
        canvas.drawBitmap(canvasBitmap, 0, 0, maskPaint);
    }
}
