package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.WorkerThread;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProfilePhotoHelper {

    private final Uri profilePhotoUri;
    private final Context context;
    private final Object profilePhotoLock = new Object();

    public ProfilePhotoHelper(Context context) {
        this.context = context.getApplicationContext();
        profilePhotoUri = Uri.fromFile(new File(this.context.getFilesDir(), "veridProfilePhoto.jpg"));
    }

    @WorkerThread
    public void setProfilePhotoFromUri(Uri imageUri, RectF cropRect) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                if (cropRect != null) {
                    Rect crop = new Rect();
                    cropRect.round(crop);
                    crop.left = Math.max(crop.left, 0);
                    crop.top = Math.max(crop.top, 0);
                    crop.right = Math.min(crop.right, bitmap.getWidth());
                    crop.bottom = Math.min(crop.bottom, bitmap.getHeight());
                    bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height());
                }
                setProfilePhoto(bitmap);
            }
        }
    }

    @WorkerThread
    public void setProfilePhoto(Bitmap bitmap) throws IOException {
        synchronized (profilePhotoLock) {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(profilePhotoUri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
            }
        }
    }

    @WorkerThread
    public Bitmap getProfilePhoto() throws IOException {
        synchronized (profilePhotoLock) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(profilePhotoUri)) {
                return BitmapFactory.decodeStream(inputStream);
            }
        }
    }

    @WorkerThread
    private Bitmap getProfilePhotoBitmap(int width) throws IOException {
        Bitmap bitmap = getProfilePhoto();
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int x = (int) ((double) bitmap.getWidth() / 2.0 - (double) size / 2.0);
        int y = (int) ((double) bitmap.getHeight() / 2.0 - (double) size / 2.0);
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, size, size);
        croppedBitmap = Bitmap.createScaledBitmap(croppedBitmap, width, width, true);
        return croppedBitmap;
    }

    @WorkerThread
    public RoundedBitmapDrawable getProfilePhotoDrawable(int width) throws IOException {
        Bitmap bitmap = getProfilePhotoBitmap(width);
        RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
        roundedBitmapDrawable.setCornerRadius((float) width / 2f);
        return roundedBitmapDrawable;
    }
}
