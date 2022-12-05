package com.appliedrec.verid.ui2;

import android.Manifest;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;

import com.appliedrec.verid.core2.Size;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class CameraPreviewHelperTest {

    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA);

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
            Matrix matrix = CameraPreviewHelper.getInstance().getViewTransformMatrix(params.imageSize.width, params.imageSize.height, params.viewSize.width, params.viewSize.height, params.sensorOrientation, params.deviceOrientation);
            RectF viewRect = new RectF(0, 0, params.viewSize.width, params.viewSize.height);
            matrix.mapRect(viewRect);
            assertEquals(finalImageSizes[i].top, viewRect.top, 0.1f);
            assertEquals(finalImageSizes[i].left, viewRect.left, 0.1f);
            assertEquals(finalImageSizes[i].bottom, viewRect.bottom, 0.1f);
            assertEquals(finalImageSizes[i].right, viewRect.right, 0.1f);
            i++;
        }
    }

    @Test
    public void testSelectCurrentDeviceCameraOutputSizes() throws Exception {
        CameraManager cameraManager = InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(CameraManager.class);
        String[] cameras = cameraManager.getCameraIdList();
        assertTrue(cameras.length > 0);
        Class<?> previewClass = new SessionView(InstrumentationRegistry.getInstrumentation().getTargetContext()).getPreviewClass();
        android.util.Size[] viewSizes = new android.util.Size[]{
                new android.util.Size(600, 600),
                new android.util.Size(800, 600),
                new android.util.Size(600, 800),
                new android.util.Size(1280, 752),
                new android.util.Size(1920, 1080)
        };
        for (String cameraId : cameras) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size[] yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            android.util.Size[] previewSizes = map.getOutputSizes(previewClass);
            android.util.Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
            for (android.util.Size viewSize : viewSizes) {
                android.util.Size[] sizes = CameraPreviewHelper.getInstance().getOutputSizes(previewSizes, yuvSizes, videoSizes, viewSize.getWidth(), viewSize.getHeight(), 0, 0);
                assertTrue(sizes.length > 0);
                Float[] sizeAspectRatios = Arrays.stream(sizes).map(size -> (float)size.getWidth()/(float)size.getHeight()).toArray(Float[]::new);
                assertTrue(sizeAspectRatios.length > 0);
                float delta = 0.01f;
                Arrays.stream(sizeAspectRatios).allMatch(sizeAspectRatio -> Math.abs(sizeAspectRatio - sizeAspectRatios[0]) < delta);
            }
        }
    }

    @Test
    public void testSelectCameraOutputSizes() {
        // Sizes taken from Nexus 9
        android.util.Size[] yuvSizes = new android.util.Size[]{
                new android.util.Size(3280, 2460),
                new android.util.Size(1640, 1230),
                new android.util.Size(3264, 2448),
                new android.util.Size(2592, 1944),
                new android.util.Size(2048, 1536),
                new android.util.Size(1920, 1440),
                new android.util.Size(1920, 1080),
                new android.util.Size(1440, 1080),
                new android.util.Size(1280, 960),
                new android.util.Size(1280, 720),
                new android.util.Size(720, 480),
                new android.util.Size(640, 480),
                new android.util.Size(352, 288),
                new android.util.Size(320, 240),
                new android.util.Size(176, 144)
        };
        android.util.Size[] previewSizes = new android.util.Size[]{
                new android.util.Size(3280, 2460),
                new android.util.Size(1640, 1230),
                new android.util.Size(3264, 2448),
                new android.util.Size(2592, 1944),
                new android.util.Size(2048, 1536),
                new android.util.Size(1920, 1440),
                new android.util.Size(1920, 1080),
                new android.util.Size(1440, 1080),
                new android.util.Size(1280, 960),
                new android.util.Size(1280, 720),
                new android.util.Size(720, 480),
                new android.util.Size(640, 480),
                new android.util.Size(352, 288),
                new android.util.Size(320, 240),
                new android.util.Size(176, 144)
        };
        android.util.Size[] videoSizes = new android.util.Size[]{
                new android.util.Size(3280, 2460),
                new android.util.Size(1640, 1230),
                new android.util.Size(3264, 2448),
                new android.util.Size(2592, 1944),
                new android.util.Size(2048, 1536),
                new android.util.Size(1920, 1440),
                new android.util.Size(1920, 1080),
                new android.util.Size(1440, 1080),
                new android.util.Size(1280, 960),
                new android.util.Size(1280, 720),
                new android.util.Size(720, 480),
                new android.util.Size(640, 480),
                new android.util.Size(352, 288),
                new android.util.Size(320, 240),
                new android.util.Size(176, 144)
        };
        android.util.Size[] viewSizes = new android.util.Size[]{
                new android.util.Size(600, 600),
                new android.util.Size(800, 600),
                new android.util.Size(600, 800),
                new android.util.Size(1280, 752),
                new android.util.Size(1920, 1080)
        };
        for (android.util.Size viewSize : viewSizes) {
            android.util.Size[] sizes = CameraPreviewHelper.getInstance().getOutputSizes(previewSizes, yuvSizes, videoSizes, viewSize.getWidth(), viewSize.getHeight(), 0, 0);
            assertTrue(sizes.length > 0);
            Float[] sizeAspectRatios = Arrays.stream(sizes).map(size -> (float)size.getWidth()/(float)size.getHeight()).toArray(Float[]::new);
            assertTrue(sizeAspectRatios.length > 0);
            float delta = 0.01f;
            Arrays.stream(sizeAspectRatios).allMatch(sizeAspectRatio -> Math.abs(sizeAspectRatio - sizeAspectRatios[0]) < delta);
        }
    }
}
