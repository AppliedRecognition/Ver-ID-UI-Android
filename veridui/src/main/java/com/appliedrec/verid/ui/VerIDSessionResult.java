package com.appliedrec.verid.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.DetectedFace;
import com.appliedrec.verid.core.Face;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class VerIDSessionResult<T extends Face> {

    public static class Capture<T extends Face> {

        private final T face;
        private final Bearing bearing;
        private final Bitmap image;
        private final Bitmap faceImage;

        @SuppressLint("CheckResult")
        public Capture(@NonNull T face, @NonNull Bearing bearing, @NonNull Bitmap image) {
            this.face = face;
            this.bearing = bearing;
            this.image = image;
            Rect cropRect = new Rect();
            face.getBounds().round(cropRect);
            cropRect.intersect(new Rect(0, 0, image.getWidth(), image.getHeight()));
            this.faceImage = Bitmap.createBitmap(image, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
        }

        @NonNull
        public T getFace() {
            return face;
        }

        @NonNull
        public Bearing getBearing() {
            return bearing;
        }

        @NonNull
        public Bitmap getImage() {
            return image;
        }

        @NonNull
        public Bitmap getFaceImage() {
            return faceImage;
        }
    }

    private final Exception error;
    private final Capture<T>[] captures;
    private final Class<T> faceType;

    VerIDSessionResult(com.appliedrec.verid.core.VerIDSessionResult input, Class<T> faceType) {
        this.faceType = faceType;
        ArrayList<Capture<T>> capturesList = new ArrayList<>();
        for (DetectedFace detectedFace : input.getAttachments()) {
            if (detectedFace.getImageUri() == null) {
                continue;
            }
            if (!faceType.isInstance(detectedFace.getFace())) {
                continue;
            }
            String imagePath = detectedFace.getImageUri().getPath();
            if (imagePath == null) {
                continue;
            }
            T face = faceType.cast(detectedFace.getFace());
            if (face == null) {
                continue;
            }
            try (FileInputStream inputStream = new FileInputStream(new File(detectedFace.getImageUri().getPath()))) {
                Bitmap image = BitmapFactory.decodeStream(inputStream);
                capturesList.add(new Capture<>(face, detectedFace.getBearing(), image));
            } catch (Exception e) {
                input = new com.appliedrec.verid.core.VerIDSessionResult(e);
            }
        }
        //noinspection unchecked
        captures = new Capture[capturesList.size()];
        capturesList.toArray(captures);
        error = input.getError();
    }

    VerIDSessionResult(Exception error, Class<T> faceType) {
        this.faceType = faceType;
        //noinspection unchecked
        this.captures = new Capture[0];
        this.error = error;
    }

    @Nullable
    public Exception getError() {
        return error;
    }

    @NonNull
    public Capture<T>[] getCaptures() {
        return captures;
    }

    @NonNull
    public Capture<T>[] getCaptures(@NonNull Bearing bearing) {
        ArrayList<Capture<T>> captureList = new ArrayList<>();
        for (Capture<T> capture : captures) {
            if (capture.getBearing() == bearing) {
                captureList.add(capture);
            }
        }
        //noinspection unchecked
        Capture<T>[] captures = new Capture[captureList.size()];
        captureList.toArray(captures);
        return captures;
    }

    @NonNull
    public Class<T> getFaceType() {
        return faceType;
    }
}
