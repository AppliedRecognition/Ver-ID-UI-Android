package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.ExifOrientation;
import com.appliedrec.verid.core2.IImageProvider;
import com.appliedrec.verid.core2.ImageUtils;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImageBitmap;
import com.appliedrec.verid.core2.session.IImage;
import com.appliedrec.verid.core2.session.IImageIterator;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link IImageIterator}
 * @since 2.0.0
 */
@Keep
public class VerIDImageIterator implements IImageIterator {

    private final SynchronousQueue<IImageProvider> imageQueue = new SynchronousQueue<>();
    private final AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private final AtomicBoolean isMirrored = new AtomicBoolean(false);
//    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicReference<ImageUtils> imageUtils = new AtomicReference<>(null);
    private WeakReference<Context> contextRef = new WeakReference<>(null);

    @NonNull
    @Override
    public Iterator<IImageProvider> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return imageUtils.get() != null;
//        return isActive.get();
    }

    @Override
    public IImageProvider next() {
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
     * @deprecated Use {@link #VerIDImageIterator(Context)}
     */
    @Keep
    @Deprecated
    public VerIDImageIterator(VerID verID) {
        this.contextRef = new WeakReference<>(verID.getContext().get());
    }

    @Keep
    public VerIDImageIterator(Context context) {
        this.contextRef = new WeakReference<>(context.getApplicationContext());
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null || imageUtils.get() == null) {
                return;
            }
            queueImage(new MediaImageImage(image, exifOrientation.get()));
        }
    }

    private void queueImage(IImage<?> image) {
        try {
            ImageUtils imageUtils = this.imageUtils.get();
            if (imageUtils == null) {
                return;
            }
            com.appliedrec.verid.core2.Image verIDImage = imageUtils.verIDImageFromImageSource(image);
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
        Context context = contextRef.get();
        if (context != null) {
            imageUtils.set(new ImageUtils(context));
        }
//        isActive.set(true);
    }

    @Override
    public void deactivate() {
        if (imageUtils.get() == null) {
            return;
        }
        imageUtils.get().close();
        imageUtils.set(null);
//        isActive.set(false);
    }
}
