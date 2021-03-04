package com.appliedrec.verid.ui2;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Keep;

/**
 * Frame layout with fixed aspect ratio
 */
@Keep
public class FixedAspectRatioFrameLayout extends FrameLayout {

    private int aspectRatioWidth;
    private int aspectRatioHeight;

    /**
     * Constructor
     * @param context Context
     */
    @Keep
    public FixedAspectRatioFrameLayout(Context context)
    {
        super(context);
    }

    /**
     * Constructor
     * @param context Context
     * @param width Aspect ratio width
     * @param height Aspect ratio height
     */
    @Keep
    public FixedAspectRatioFrameLayout(Context context, int width, int height) {
        super(context);
        aspectRatioWidth = width;
        aspectRatioHeight = height;
    }

    /**
     * Constructor
     * @param context Context
     * @param attrs Attribute set
     */
    @Keep
    public FixedAspectRatioFrameLayout(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        init(context, attrs);
    }

    /**
     * Constructor
     * @param context Context
     * @param attrs Attribute set
     * @param defStyle Style
     */
    @Keep
    public FixedAspectRatioFrameLayout(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs)
    {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FixedAspectRatioFrameLayout);

        aspectRatioWidth = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspect_ratio_width, 1);
        aspectRatioHeight = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspect_ratio_height, 1);

        a.recycle();
    }
    // **overrides**

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
    {
        int originalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int originalHeight = MeasureSpec.getSize(heightMeasureSpec);
        int calculatedHeight = originalWidth * aspectRatioHeight / aspectRatioWidth;
        int finalWidth, finalHeight;
        if (calculatedHeight > originalHeight) {
            finalWidth = originalHeight * aspectRatioWidth / aspectRatioHeight;
            finalHeight = originalHeight;
        } else {
            finalWidth = originalWidth;
            finalHeight = calculatedHeight;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY));
    }
}
