package com.appliedrec.verid.ui2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.ui2.ui.theme.SessionTheme;

public class OvalMaskView extends View {

    private Bitmap maskBitmap;
    private Canvas tempCanvas;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF maskRect;

    public SessionTheme sessionTheme = SessionTheme.getDefault();

    public OvalMaskView(Context context) {
        this(context, null);
    }

    public OvalMaskView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @UiThread
    public void setMaskRect(RectF maskRect) {
        this.maskRect = maskRect;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (tempCanvas != null) {
            tempCanvas.setBitmap(null);
            tempCanvas = null;
        }
        if (maskBitmap != null) {
            maskBitmap.recycle();
            maskBitmap = null;
        }
        maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        tempCanvas = new Canvas(maskBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (maskBitmap == null || tempCanvas == null || maskRect == null) {
            return;
        }
        int backgroundColor;
        switch (getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                backgroundColor = sessionTheme.getBackgroundColorDarkTheme();
                break;
            default:
                backgroundColor = sessionTheme.getBackgroundColorLightTheme();
                break;
        }
        tempCanvas.drawColor(backgroundColor);
        tempCanvas.drawOval(this.maskRect, maskPaint);
        canvas.drawBitmap(maskBitmap, 0, 0, null);
    }
}
