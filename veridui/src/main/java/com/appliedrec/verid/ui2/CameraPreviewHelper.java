package com.appliedrec.verid.ui2;

import android.graphics.Matrix;
import android.util.Size;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for camera previews
 * @since 2.4.0
 */
public class CameraPreviewHelper {

    private static class InstanceHolder {
        private static CameraPreviewHelper cameraPreviewHelper = new CameraPreviewHelper();
    }

    /**
     * @return Singleton instance of `CameraPreviewHelper`
     * @since 2.4.0
     */
    public static CameraPreviewHelper getInstance() {
        return InstanceHolder.cameraPreviewHelper;
    }

    /**
     * Get matrix to transform a texture view with camera preview to upright orientation and to correct its aspect ratio
     * @param imageWidth Camera image width
     * @param imageHeight Camera image height
     * @param viewWidth Width of the view containing the camera preview texture
     * @param viewHeight Height of the view containing the camera preview texture
     * @param sensorOrientation Camera sensor orientation relative to the device held upright
     * @param deviceRotation Rotation of the device
     * @return Matrix
     * @since 2.4.0
     */
    public Matrix getViewTransformMatrix(int imageWidth, int imageHeight, int viewWidth, int viewHeight, int sensorOrientation, int deviceRotation) {
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

    public Size getCorrectedTextureSize(int imageWidth, int imageHeight, int viewWidth, int viewHeight, int sensorOrientation, int deviceRotation) {
        SizeF imageSize;
        if ((sensorOrientation - deviceRotation) % 180 == 0) {
            imageSize = new SizeF(imageWidth, imageHeight);
        } else {
            imageSize = new SizeF(imageHeight, imageWidth);
        }
        float imageAspectRatio = imageSize.getWidth() / imageSize.getHeight();
        float viewAspectRatio = (float)viewWidth / (float)viewHeight;
        float scale;
        if (imageAspectRatio > viewAspectRatio) {
            scale = (float) viewHeight / imageSize.getHeight();
        } else {
            scale = (float) viewWidth / imageSize.getWidth();
        }
        SizeF viewSize;
        if (deviceRotation % 180 == 0) {
            viewSize = new SizeF(imageSize.getWidth() * scale, imageSize.getHeight() * scale);
        } else {
            viewSize = new SizeF(imageSize.getHeight() * scale, imageSize.getWidth() * scale);
        }
        return new Size((int)viewSize.getWidth(), (int)viewSize.getHeight());
    }

    /**
     * Select a set of camera output sizes
     * @param previewSizes Preview size candidates
     * @param imageReaderSizes Image reader size candidates
     * @param videoSizes Video size candidates
     * @param aspectRatio Desired aspect ratio
     * @return Set of output sizes best matching the aspect ratio
     * @since 2.4.0
     */
    public Size[] getOutputSizes(Size[] previewSizes, Size[] imageReaderSizes, Size[] videoSizes, float aspectRatio) {
        int minArea = 320 * 240;
        HashMap<Float,ArrayList<Size>> previewAspectRatios = getAspectRatioSizes(previewSizes);
        HashMap<Float,ArrayList<Size>> imageReaderAspectRatios = getAspectRatioSizes(imageReaderSizes);
        HashMap<Float,ArrayList<Size>> videoAspectRatios = getAspectRatioSizes(videoSizes);
        HashMap<Float,Size[]> candidates = new HashMap<>();
        Comparator<Size> sizeComparator = Comparator.comparingInt(size -> size.getWidth() * size.getHeight());
        for (Map.Entry<Float,ArrayList<Size>> entry : previewAspectRatios.entrySet()) {
            ArrayList<Size> imageReaderCandidates = getSizesMatchingAspectRatio(entry.getKey(), imageReaderAspectRatios, minArea);
            ArrayList<Size> videoCandidates = getSizesMatchingAspectRatio(entry.getKey(), videoAspectRatios, minArea);
            if (imageReaderCandidates.isEmpty() || videoCandidates.isEmpty()) {
                continue;
            }
            Size[] sizes = new Size[3];
            sizes[0] = Collections.max(entry.getValue(), sizeComparator);
            sizes[1] = Collections.min(imageReaderCandidates, sizeComparator);
            sizes[2] = Collections.min(videoCandidates, sizeComparator);
            candidates.put(entry.getKey(), sizes);
        }
        if (candidates.isEmpty()) {
            return new Size[]{previewSizes[0],imageReaderSizes[0],videoSizes[0]};
        }
        Map.Entry<Float,Size[]> bestEntry = Collections.min(candidates.entrySet(), (lhs, rhs) -> {
            float lhsAspectRatioDiff = Math.abs(lhs.getKey() - aspectRatio);
            float rhsAspectRatioDiff = Math.abs(rhs.getKey() - aspectRatio);
            return (int)(lhsAspectRatioDiff * 1000f - rhsAspectRatioDiff * 1000f);
        });
        return bestEntry.getValue();
    }

    private HashMap<Float,ArrayList<Size>> getAspectRatioSizes(Size[] sizes) {
        HashMap<Float,ArrayList<Size>> aspectRatios = new HashMap<>();
        for (Size size : sizes) {
            float aspectRatio = (float)size.getWidth()/(float)size.getHeight();
            if (!aspectRatios.containsKey(aspectRatio)) {
                aspectRatios.put(aspectRatio, new ArrayList<>());
            }
            //noinspection ConstantConditions
            aspectRatios.get(aspectRatio).add(size);
        }
        return aspectRatios;
    }

    private ArrayList<Size> getSizesMatchingAspectRatio(float aspectRatio, HashMap<Float,ArrayList<Size>> candidates, int minArea) {
        float aspectRatioTolerance = 0.01f;
        ArrayList<Size> sizes = new ArrayList<>();
        for (Map.Entry<Float,ArrayList<Size>> entry : candidates.entrySet()) {
            if (Math.abs(entry.getKey() - aspectRatio) < aspectRatioTolerance) {
                List<Size> applicableSizes = entry.getValue().stream().filter((size) -> size.getWidth() * size.getHeight() >= minArea).collect(Collectors.toList());
                sizes.addAll(applicableSizes);
            }
        }
        return sizes;
    }
}
