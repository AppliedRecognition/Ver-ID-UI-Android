package com.appliedrec.verid.ui2;

import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.ExifOrientation;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.IImage;
import com.appliedrec.verid.core2.session.IImageIterator;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link IImageIterator}
 * @since 2.0.0
 */
@Keep
public class VerIDImageIterator implements IImageIterator {

    private final SynchronousQueue<VerIDImage<?>> imageQueue = new SynchronousQueue<>();
    private final AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private final AtomicBoolean isMirrored = new AtomicBoolean(false);
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final VerID verID;

    @NonNull
    @Override
    public Iterator<VerIDImage<?>> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public VerIDImage<?> next() {
        try {
            return imageQueue.take();
        } catch (InterruptedException ignore) {
            return null;
        }
    }

    private static class MediaImageImage implements IImage<Image> {

        private final Image mediaImage;
        private final int exifOrientation;

        public MediaImageImage(Image mediaImage, int exifOrientation) {
            this.mediaImage = mediaImage;
            this.exifOrientation = exifOrientation;
        }

        @Override
        public Image getSourceImage() {
            return mediaImage;
        }

        @Override
        public Rect getCropRect() {
            return mediaImage.getCropRect();
        }

        @Override
        public int getPlaneCount() {
            return mediaImage.getPlanes().length;
        }

        @Override
        public ByteBuffer getBufferOfPlane(int plane) {
            return mediaImage.getPlanes()[plane].getBuffer();
        }

        @Override
        public int getRowStrideOfPlane(int plane) {
            return mediaImage.getPlanes()[plane].getRowStride();
        }

        @Override
        public int getPixelStrideOfPlane(int plane) {
            return mediaImage.getPlanes()[plane].getPixelStride();
        }

        @Override
        public int getWidth() {
            return mediaImage.getWidth();
        }

        @Override
        public int getHeight() {
            return mediaImage.getHeight();
        }

        @Override
        public int getExifOrientation() {
            return exifOrientation;
        }

        @Override
        public void close() {
            mediaImage.close();
        }
    }

    /**
     * Constructor
     * @param verID Instance of {@link VerID}
     */
    @Keep
    public VerIDImageIterator(VerID verID) {
        this.verID = verID;
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null || !isActive.get()) {
                return;
            }
            queueImage(new MediaImageImage(image, exifOrientation.get()));
        }
    }

    private void queueImage(IImage<?> image) {
        try {
            VerIDImage<?> verIDImage = verID.getFaceDetection().createVerIDImage(image);
            verIDImage.setIsMirrored(isMirrored.get());
            imageQueue.put(verIDImage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setExifOrientation(@ExifOrientation int exifOrientation) {
        boolean isMirrored;
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                exifOrientation = ExifInterface.ORIENTATION_NORMAL;
                isMirrored = true;
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                isMirrored = true;
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                isMirrored = true;
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                isMirrored = true;
                break;
            default:
                isMirrored = false;
        }
        this.exifOrientation.set(exifOrientation);
        this.isMirrored.set(isMirrored);
    }

    @Override
    public void activate() {
        isActive.set(true);
    }

    @Override
    public void deactivate() {
        isActive.set(false);
    }
}
