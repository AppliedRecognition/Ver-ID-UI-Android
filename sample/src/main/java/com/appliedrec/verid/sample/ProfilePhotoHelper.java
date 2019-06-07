package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core.ImageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;

class ProfilePhotoHelper {

    private boolean isSettingProfilePhoto = false;
    private Uri profilePhotoUri;
    private Object profilePhotoLock = new Object();
    private Context context;
    private ImageUtils imageUtils;

    ProfilePhotoHelper(Context context) {
        this.context = context.getApplicationContext();
        profilePhotoUri = Uri.fromFile(new File(this.context.getFilesDir(), "veridProfilePhoto.jpg"));
        imageUtils = new ImageUtils(this.context);
    }

    void setProfilePhotoUri(final Uri imageUri) {
        synchronized (profilePhotoLock) {
            while (isSettingProfilePhoto) {
                try {
                    profilePhotoLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isSettingProfilePhoto = true;
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(imageUri.getPath());
                    if (bitmap != null) {
                        byte[] grayscale = imageUtils.bitmapToGrayscale(bitmap, ExifInterface.ORIENTATION_NORMAL);
                        Bitmap grayscaleBitmap = imageUtils.grayscaleToBitmap(grayscale, bitmap.getWidth(), bitmap.getHeight());
                        bitmap.recycle();
                        OutputStream outputStream = context.getContentResolver().openOutputStream(profilePhotoUri);
                        grayscaleBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                isSettingProfilePhoto = false;
                synchronized (profilePhotoLock) {
                    profilePhotoLock.notifyAll();
                }
            }
        });
    }

    Uri getProfilePhotoUri() {
        synchronized (profilePhotoLock) {
            while (isSettingProfilePhoto) {
                try {
                    profilePhotoLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return profilePhotoUri;
        }
    }
}
