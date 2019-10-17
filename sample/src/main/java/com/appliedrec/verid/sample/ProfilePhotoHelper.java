package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import io.reactivex.Completable;
import io.reactivex.Single;

class ProfilePhotoHelper {

    private Uri profilePhotoUri;
    private Context context;
    private Semaphore photoSemaphore = new Semaphore(1);

    ProfilePhotoHelper(Context context) {
        this.context = context.getApplicationContext();
        profilePhotoUri = Uri.fromFile(new File(this.context.getFilesDir(), "veridProfilePhoto.jpg"));
    }

    Completable setProfilePhotoFromUri(Uri imageUri, RectF cropRect) {
        return Completable.create(emitter -> {
            try {
                photoSemaphore.acquire();
                Bitmap bitmap = BitmapFactory.decodeFile(imageUri.getPath());
                if (bitmap != null) {
                    if (cropRect != null) {
                        Rect crop = new Rect();
                        cropRect.round(crop);
                        crop.left = Math.max(crop.left,0);
                        crop.top = Math.max(crop.top,0);
                        crop.right = Math.min(crop.right,bitmap.getWidth());
                        crop.bottom = Math.min(crop.bottom,bitmap.getHeight());
                        bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height());
                    }
                    OutputStream outputStream = context.getContentResolver().openOutputStream(profilePhotoUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                }
                photoSemaphore.release();
                emitter.onComplete();
            } catch (FileNotFoundException e) {
                photoSemaphore.release();
                emitter.onError(e);
            }
        });
    }

    Single<Bitmap> getProfilePhotoBitmap() {
        return getProfilePhotoUri()
                .flatMap(uri -> observer -> {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
                        if (bitmap != null) {
                            observer.onSuccess(bitmap);
                        } else {
                            throw new Exception("Unable to decode bitmap");
                        }
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                });
    }

    Single<Bitmap> getProfilePhotoBitmap(int width) {
        return getProfilePhotoBitmap()
                .flatMap(bitmap -> observer -> {
                    int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
                    int x = (int) ((double) bitmap.getWidth() / 2.0 - (double) size / 2.0);
                    int y = (int) ((double) bitmap.getHeight() / 2.0 - (double) size / 2.0);
                    Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, size, size);
                    croppedBitmap = Bitmap.createScaledBitmap(croppedBitmap, width, width, true);
                    observer.onSuccess(croppedBitmap);
                });
    }

    Single<RoundedBitmapDrawable> getProfilePhotoDrawable(int width) {
        return getProfilePhotoBitmap(width)
                .flatMap(bitmap -> observer -> {
                    RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
                    roundedBitmapDrawable.setCornerRadius((float) width / 2f);
                    observer.onSuccess(roundedBitmapDrawable);
                });
    }

    Single<Uri> getProfilePhotoUri() {
        return Single.create(emitter -> {
            try {
                photoSemaphore.acquire();
                Uri uri = profilePhotoUri;
                photoSemaphore.release();
                emitter.onSuccess(uri);
            } catch (InterruptedException e) {
                emitter.onError(e);
            }
        });
    }

//    Uri getProfilePhotoUri() {
//        synchronized (profilePhotoLock) {
//            while (isSettingProfilePhoto) {
//                try {
//                    profilePhotoLock.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            return profilePhotoUri;
//        }
//    }
}
