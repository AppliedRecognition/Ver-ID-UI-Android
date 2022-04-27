package com.appliedrec.verid.ui2;

import android.graphics.Matrix;
import android.util.SizeF;

public class CameraPreviewHelper {

    public static Matrix getViewTransformMatrix(int imageWidth, int imageHeight, int viewWidth, int viewHeight, int sensorOrientation, int deviceRotation) {
        Matrix matrix = new Matrix();
        float viewAspectRatio = (float)viewWidth / (float)viewHeight;
        SizeF rotatedSize = new SizeF(viewWidth, viewHeight);
        SizeF correctedImageSize;
        if ((sensorOrientation - deviceRotation) % 180 == 0) {
            correctedImageSize = new SizeF(imageWidth, imageHeight);
        } else {
            correctedImageSize = new SizeF(imageHeight, imageWidth);
        }
        float imageAspectRatio = correctedImageSize.getWidth() / correctedImageSize.getHeight();
        if (deviceRotation % 180 != 0) {
            rotatedSize = new SizeF(viewHeight, viewWidth);
        }
        float scale;
        if (imageAspectRatio > viewAspectRatio) {
            scale = (float)viewHeight / correctedImageSize.getHeight();
        } else {
            scale = (float)viewWidth / correctedImageSize.getWidth();
        }
        SizeF finalImageSize = new SizeF(correctedImageSize.getWidth()*scale, correctedImageSize.getHeight()*scale);
        matrix.setRotate(-deviceRotation, (float)viewWidth/2f, (float)viewHeight/2f);
        matrix.postScale(finalImageSize.getWidth()/rotatedSize.getWidth(), finalImageSize.getHeight()/rotatedSize.getHeight(), (float)viewWidth/2f, (float)viewHeight/2f);
        return matrix;
    }
}
