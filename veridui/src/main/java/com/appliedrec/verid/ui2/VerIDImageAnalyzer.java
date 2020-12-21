package com.appliedrec.verid.ui2;

import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.IImage;
import com.appliedrec.verid.core2.session.IImageFlowable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.FlowableEmitter;

public class VerIDImageAnalyzer implements IImageFlowable, ImageReader.OnImageAvailableListener, DefaultLifecycleObserver {

    private final SynchronousQueue<VerIDImage<?>> imageQueue = new SynchronousQueue<>();
    private final AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private final AtomicBoolean isMirrored = new AtomicBoolean(false);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private Thread subscribeThread;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private VerID verID;
    private final Object veridLock = new Object();

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

    public VerIDImageAnalyzer(AppCompatActivity lifecycleOwner) {
        lifecycleOwner.getLifecycle().addObserver(this);
        isStarted.set(lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED));
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null || !isStarted.get()) {
                return;
            }
            queueImage(new MediaImageImage(image, exifOrientation.get()));
        }
    }

    public void setVerID(VerID verID) {
        synchronized (veridLock) {
            this.verID = verID;
            veridLock.notifyAll();
        }
    }

    private void queueImage(IImage<?> image) {
        try {
            synchronized (veridLock) {
                while (verID == null) {
                    veridLock.wait();
                }
            }
            VerIDImage<?> verIDImage = verID.getFaceDetection().createVerIDImage(image);
            verIDImage.setIsMirrored(isMirrored.get());
            imageQueue.put(verIDImage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fail(Throwable throwable) {
        this.failure.set(throwable);
        if (subscribeThread != null) {
            subscribeThread.interrupt();
        }
    }

    Optional<Throwable> getFailure() {
        return Optional.ofNullable(failure.get());
    }

    @IntDef({ExifInterface.ORIENTATION_NORMAL,ExifInterface.ORIENTATION_ROTATE_90,ExifInterface.ORIENTATION_ROTATE_180,ExifInterface.ORIENTATION_ROTATE_270,ExifInterface.ORIENTATION_FLIP_HORIZONTAL,ExifInterface.ORIENTATION_FLIP_VERTICAL,ExifInterface.ORIENTATION_TRANSPOSE,ExifInterface.ORIENTATION_TRANSVERSE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExifOrientation{}

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
    public void subscribe(@io.reactivex.rxjava3.annotations.NonNull FlowableEmitter<VerIDImage<?>> emitter) {
        subscribeThread = Thread.currentThread();
        while (!emitter.isCancelled()) {
            if (getFailure().isPresent()) {
                emitter.onError(getFailure().get());
                return;
            }
            try {
                VerIDImage<?> image = imageQueue.take();
                emitter.onNext(image);
            } catch (InterruptedException ignore) {
            }
        }
    }

    private void cropByteBufferToSize(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int width, int height, int bytesPerRow) {
        dstBuffer.rewind();
        byte[] row = new byte[width];
        for (int i=0; i<height*bytesPerRow; i+=bytesPerRow) {
            srcBuffer.position(i);
            srcBuffer.get(row, 0, width);
            dstBuffer.put(row);
        }
    }

    private int getRotationCompensation() {
        switch (exifOrientation.get()) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isStarted.set(true);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isStarted.set(false);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
    }
}
