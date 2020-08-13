package com.appliedrec.verid.ui2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.IImage;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.YUVToRGBConverter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.FlowableEmitter;

public class VerIDImageAnalyzer implements ImageAnalysis.Analyzer, IImageFlowable, ImageReader.OnImageAvailableListener, DefaultLifecycleObserver {

    private final SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private final AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private final AtomicBoolean isMirrored = new AtomicBoolean(false);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private Thread subscribeThread;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private YUVToRGBConverter yuvToRGBConverter;

    private static class MediaImageImage implements IImage<Image> {

        private final Image mediaImage;

        public MediaImageImage(Image mediaImage) {
            this.mediaImage = mediaImage;
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
        public void close() {
            mediaImage.close();
        }
    }

    private static class ImageProxyImage implements IImage<ImageProxy> {

        private final ImageProxy imageProxy;

        ImageProxyImage(ImageProxy imageProxy) {
            this.imageProxy = imageProxy;
        }

        @Override
        public ImageProxy getSourceImage() {
            return imageProxy;
        }

        @Override
        public Rect getCropRect() {
            return imageProxy.getCropRect();
        }

        @Override
        public int getPlaneCount() {
            return imageProxy.getPlanes().length;
        }

        @Override
        public ByteBuffer getBufferOfPlane(int plane) {
            return imageProxy.getPlanes()[plane].getBuffer();
        }

        @Override
        public int getRowStrideOfPlane(int plane) {
            return imageProxy.getPlanes()[plane].getRowStride();
        }

        @Override
        public int getPixelStrideOfPlane(int plane) {
            return imageProxy.getPlanes()[plane].getPixelStride();
        }

        @Override
        public int getWidth() {
            return imageProxy.getWidth();
        }

        @Override
        public int getHeight() {
            return imageProxy.getHeight();
        }

        @Override
        public void close() {
            imageProxy.close();
        }
    }

    public VerIDImageAnalyzer(AppCompatActivity lifecycleOwner) {
        lifecycleOwner.getLifecycle().addObserver(this);
        isStarted.set(lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED));
        if (isStarted.get()) {
            yuvToRGBConverter = new YUVToRGBConverter(lifecycleOwner);
        }
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null || !isStarted.get()) {
                return;
            }
            queueImage(new MediaImageImage(image));
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private void queueImage(IImage image) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            yuvToRGBConverter.imageToBitmap(image, bitmap);
            VerIDImage verIDImage = new VerIDImage(bitmap, exifOrientation.get());
            verIDImage.setIsMirrored(isMirrored.get());
            if (image.getSourceImage() instanceof Image) {
                verIDImage.setMediaImage((Image)image.getSourceImage());
            } else if (image.getSourceImage() instanceof ImageProxy) {
                verIDImage.setMediaImage(((ImageProxy)image.getSourceImage()).getImage());
            }
            verIDImage.setMediaImageExifOrientation(exifOrientation.get());
            try {
                imageQueue.put(verIDImage);
            } catch (InterruptedException ignore) {
            }
        } catch (Exception e) {
            return;
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
        queueImage(new ImageProxyImage(image));
        image.close();
    }

//    private VerIDImage verIDImageFromImage(IImage image) {
//        int yRowStride = image.getRowStride(0);
//        int uRowStride = image.getRowStride(1);
//        int vRowStride = image.getRowStride(2);
//        int width = image.getWidth();
//        int height = image.getHeight();
//
//        ByteBuffer yBuffer = image.getBuffer(0); // Y
//        ByteBuffer uBuffer = image.getBuffer(1); // U
//        ByteBuffer vBuffer = image.getBuffer(2); // V
//
//        ByteBuffer uCropped = ByteBuffer.allocateDirect(width/2*height/2);
//        ByteBuffer vCropped = ByteBuffer.allocateDirect(width/2*height/2);
//
//        ByteBuffer nv21Buffer = ByteBuffer.allocateDirect(width*height+width*height/2);
//
//        cropByteBufferToSize(yBuffer, nv21Buffer, width, height, yRowStride);
//        cropByteBufferToSize(uBuffer, uCropped, width / 2, height / 2, uRowStride);
//        cropByteBufferToSize(vBuffer, vCropped, width / 2, height / 2, vRowStride);
//
//        for (int i=0; i<uCropped.capacity(); i++) {
//            nv21Buffer.put(vCropped.get(i)).put(uCropped.get(i));
//        }
//        byte[] nv21 = new byte[nv21Buffer.capacity()];
//        nv21Buffer.rewind();
//        nv21Buffer.get(nv21);
//
//        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
//        VerIDImage verIDImage = new VerIDImage(yuvImage, exifOrientation.get());
//        verIDImage.setIsMirrored(isMirrored.get());
//        return verIDImage;
//    }

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
        if (yuvToRGBConverter == null && owner instanceof Context) {
            yuvToRGBConverter = new YUVToRGBConverter((Context)owner);
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isStarted.set(false);
        if (yuvToRGBConverter != null) {
            yuvToRGBConverter.dispose();
            yuvToRGBConverter = null;
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
    }
}
