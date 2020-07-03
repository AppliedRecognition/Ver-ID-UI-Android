package com.appliedrec.verid.ui2;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.IImageFlowable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.FlowableEmitter;

public class VerIDImageAnalyzer implements ImageAnalysis.Analyzer, IImageFlowable, ImageReader.OnImageAvailableListener, DefaultLifecycleObserver {

    private final SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private AtomicBoolean isMirrored = new AtomicBoolean(false);
    private AtomicReference<Throwable> failure = new AtomicReference<>();
    private Thread subscribeThread;
    private AtomicBoolean isStarted = new AtomicBoolean(false);

    private interface IImage {
        int getRowStride(int plane);
        ByteBuffer getBuffer(int plane);
        int getWidth();
        int getHeight();
    }

    public VerIDImageAnalyzer(LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getLifecycle().addObserver(this);
        isStarted.set(lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED));
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null || !isStarted.get()) {
                return;
            }
            IImage imageImpl = new IImage() {
                @Override
                public int getRowStride(int plane) {
                    return image.getPlanes()[plane].getRowStride();
                }

                @Override
                public ByteBuffer getBuffer(int plane) {
                    return image.getPlanes()[plane].getBuffer();
                }

                @Override
                public int getWidth() {
                    return image.getWidth();
                }

                @Override
                public int getHeight() {
                    return image.getHeight();
                }
            };
            VerIDImage verIDImage = verIDImageFromImage(imageImpl);
            try {
                imageQueue.put(verIDImage);
            } catch (InterruptedException ignore) {
            }
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
    public void analyze(@NonNull ImageProxy image) {
        if (!isStarted.get()) {
            image.close();
            return;
        }
        IImage imageImpl = new IImage() {
            @Override
            public int getRowStride(int plane) {
                return image.getPlanes()[plane].getRowStride();
            }

            @Override
            public ByteBuffer getBuffer(int plane) {
                return image.getPlanes()[plane].getBuffer();
            }

            @Override
            public int getWidth() {
                return image.getWidth();
            }

            @Override
            public int getHeight() {
                return image.getHeight();
            }
        };
        VerIDImage verIDImage = verIDImageFromImage(imageImpl);
        try {
            imageQueue.put(verIDImage);
        } catch (InterruptedException ignore) {
        }
        image.close();
    }

    private VerIDImage verIDImageFromImage(IImage image) {
        int yRowStride = image.getRowStride(0);
        int uRowStride = image.getRowStride(1);
        int vRowStride = image.getRowStride(2);
        int width = image.getWidth();
        int height = image.getHeight();

        ByteBuffer yBuffer = image.getBuffer(0); // Y
        ByteBuffer uBuffer = image.getBuffer(1); // U
        ByteBuffer vBuffer = image.getBuffer(2); // V

        ByteBuffer uCropped = ByteBuffer.allocateDirect(width/2*height/2);
        ByteBuffer vCropped = ByteBuffer.allocateDirect(width/2*height/2);

        ByteBuffer nv21Buffer = ByteBuffer.allocateDirect(width*height+width*height/2);

        cropByteBufferToSize(yBuffer, nv21Buffer, width, height, yRowStride);
        cropByteBufferToSize(uBuffer, uCropped, width / 2, height / 2, uRowStride);
        cropByteBufferToSize(vBuffer, vCropped, width / 2, height / 2, vRowStride);

        for (int i=0; i<uCropped.capacity(); i++) {
            nv21Buffer.put(vCropped.get(i)).put(uCropped.get(i));
        }
        byte[] nv21 = new byte[nv21Buffer.capacity()];
        nv21Buffer.rewind();
        nv21Buffer.get(nv21);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        VerIDImage verIDImage = new VerIDImage(yuvImage, exifOrientation.get());
        verIDImage.setIsMirrored(isMirrored.get());
        return verIDImage;
    }

    @Override
    public void subscribe(@io.reactivex.rxjava3.annotations.NonNull FlowableEmitter<VerIDImage> emitter) {
        subscribeThread = Thread.currentThread();
        while (!emitter.isCancelled()) {
            if (getFailure().isPresent()) {
                emitter.onError(getFailure().get());
                return;
            }
            try {
                VerIDImage image = imageQueue.take();
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
