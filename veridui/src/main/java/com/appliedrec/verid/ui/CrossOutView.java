package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by jakub on 18/08/2017.
 */

public class CrossOutView extends View {

    Paint linePaint;

    public CrossOutView(Context context) {
        super(context);
        init();
    }

    public CrossOutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(getResources().getDisplayMetrics().density * 11f);
        linePaint.setColor(Color.argb(128, 0xFF, 0x00, 0x00));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getHeight(), getWidth(), 0, linePaint);
    }
}
