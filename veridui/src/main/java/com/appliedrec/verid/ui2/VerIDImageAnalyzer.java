package com.appliedrec.verid.ui2;

import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.IImageFlowable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.FlowableEmitter;

public class VerIDImageAnalyzer implements ImageAnalysis.Analyzer, IImageFlowable {

    private final SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private AtomicInteger exifOrientation = new AtomicInteger(ExifInterface.ORIENTATION_NORMAL);
    private AtomicBoolean isMirrored = new AtomicBoolean(false);

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
        long start = System.currentTimeMillis();
        int yRowStride = image.getPlanes()[0].getRowStride();
        int uRowStride = image.getPlanes()[1].getRowStride();
        int vRowStride = image.getPlanes()[2].getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        byte[] y = cropBitmapToSize(yBuffer, width, height, yRowStride);
        byte[] u = cropBitmapToSize(uBuffer, width/2, height/2, uRowStride);
        byte[] v = cropBitmapToSize(vBuffer, width/2, height/2, vRowStride);

        byte[] nv21 = new byte[y.length+u.length+v.length];
        System.arraycopy(y, 0, nv21, 0, y.length);
        for (int i=0, j=y.length; i<u.length; i++) {
            nv21[j++] = v[i];
            nv21[j++] = u[i];
        }
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);

        try {
            VerIDImage verIDImage = new VerIDImage(yuvImage, exifOrientation.get());
            verIDImage.setIsMirrored(isMirrored.get());
            imageQueue.put(verIDImage);
        } catch (InterruptedException ignore) {
        }
        image.close();
        Log.d("Ver-ID", String.format("Converted to VerIDImage in %d ms", System.currentTimeMillis()-start));
    }

    @Override
    public void subscribe(@io.reactivex.rxjava3.annotations.NonNull FlowableEmitter<VerIDImage> emitter) {
        while (!emitter.isCancelled()) {
            try {
                VerIDImage image = imageQueue.take();
                emitter.onNext(image);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private byte[] cropBitmapToSize(ByteBuffer byteBuffer, int width, int height, int bytesPerRow) {
        byteBuffer.rewind();
        byte[] original = new byte[byteBuffer.remaining()];
        byteBuffer.get(original, 0, original.length);
        byte[] cropped = new byte[width*height];
        for (int i = 0, pos = 0; i < height*bytesPerRow; i+=bytesPerRow, pos += width) {
            System.arraycopy(original, i, cropped, pos, width);
        }
        return cropped;
    }
}
