package com.appliedrec.verid.ui2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import com.appliedrec.verid.core2.Size;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class CameraPreviewHelperTest {

    private static class DeviceParams {
        final Size viewSize;
        final Size imageSize;
        final int sensorOrientation;
        final int deviceOrientation;

        public DeviceParams(int viewWidth, int viewHeight, int imageWidth, int imageHeight, int sensorOrientation, int deviceOrientation) {
            this.viewSize = new Size(viewWidth, viewHeight);
            this.imageSize = new Size(imageWidth, imageHeight);
            this.sensorOrientation = sensorOrientation;
            this.deviceOrientation = deviceOrientation;
        }
    }

    @Test
    public void testPreviewSizeCorrection() throws Exception {
        DeviceParams nexus9Portrait = new DeviceParams(1536, 1952,1472, 1104, 90, 0);
        DeviceParams nexus9Landscape = new DeviceParams(2048, 1440, 1472, 1104, 90, 90);
        DeviceParams eloLandscape = new DeviceParams(1280, 752, 2592, 1944, 0, 0);
        DeviceParams[] deviceParams = new DeviceParams[]{nexus9Portrait, nexus9Landscape, eloLandscape};

        RectF[] finalImageSizes = new RectF[]{
                new RectF(0, -48, 1536, 2000),
                new RectF(0, -48, 2048, 1488),
                new RectF(0, -104, 1280, 856)
        };

        String[] imageNames = new String[]{
                "Nexus portrait", "Nexus landscape", "Elo landscape"
        };
        int i=0;
        Paint topLeftPaint = new Paint();
        topLeftPaint.setColor(Color.RED);
        Paint topRightPaint = new Paint();
        topRightPaint.setColor(Color.GREEN);
        Paint bottomLeftPaint = new Paint();
        bottomLeftPaint.setColor(Color.BLUE);
        Paint bottomRightPaint = new Paint();
        bottomRightPaint.setColor(Color.YELLOW);
        for (DeviceParams params : deviceParams) {
            try (InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(String.format("camera_preview_tests/%s.png", imageNames[i]))) {
                Bitmap image = BitmapFactory.decodeStream(inputStream);
                Matrix matrix = CameraPreviewHelper.getInstance().getViewTransformMatrix(params.imageSize.width, params.imageSize.height, params.viewSize.width, params.viewSize.height, params.sensorOrientation, params.deviceOrientation);
                Bitmap corrected = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
                RectF viewRect = new RectF(0, 0, params.viewSize.width, params.viewSize.height);
                matrix.mapRect(viewRect);
                assertEquals(finalImageSizes[i].top, viewRect.top, 0.1f);
                assertEquals(finalImageSizes[i].left, viewRect.left, 0.1f);
                assertEquals(finalImageSizes[i].bottom, viewRect.bottom, 0.1f);
                assertEquals(finalImageSizes[i].right, viewRect.right, 0.1f);
                i++;
            }
        }
    }
}
