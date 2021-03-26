package com.appliedrec.verid.ui2;

import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.UiThread;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.ui2.databinding.ActivityResultBinding;

import java.util.Optional;

public class SessionSuccessActivity extends SessionResultActivity {

    private ActivityResultBinding activityResultBinding;
    private RoundedBitmapDrawable faceImageDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityResultBinding = ActivityResultBinding.inflate(getLayoutInflater());
        if (faceImageDrawable != null) {
            activityResultBinding.faceImageView.setImageDrawable(faceImageDrawable);
        }
        super.onCreate(savedInstanceState);
        setContentView(activityResultBinding.getRoot());
        activityResultBinding.textView.setText(translate("Great. Session succeeded."));
        runOnUiThread(this::showFaceImage);
    }

    @UiThread
    private void showFaceImage() {
        Optional<FaceCapture> faceCaptureOptional = getSessionResult().getFirstFaceCapture(Bearing.STRAIGHT);
        faceCaptureOptional.ifPresent(faceCapture -> {
            Bitmap image = faceCapture.getFaceImage();
            int size = Math.min(image.getWidth(), image.getHeight());
            Bitmap croppedBitmap = Bitmap.createBitmap(image, image.getWidth()/2-size/2, image.getHeight()/2-size/2, size, size);
            faceImageDrawable = RoundedBitmapDrawableFactory.create(getResources(), croppedBitmap);
            faceImageDrawable.setCornerRadius((float)size/2f);
            if (activityResultBinding != null) {
                activityResultBinding.faceImageView.setImageDrawable(faceImageDrawable);
            }
        });
    }
}
