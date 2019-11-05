package com.appliedrec.verid.ui;

import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.VerIDImage;
import com.appliedrec.verid.core.VerIDSessionResult;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VerIDSessionFragmentCamerax extends Fragment implements IVerIDSessionFragment {


    @Override
    public void startCamera() {

    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, @Nullable EulerAngle offsetAngleFromBearing) {

    }

    @Override
    public void clearCameraOverlay() {

    }

    @Override
    public void clearCameraPreview() {

    }

    @Override
    public VerIDImage dequeueImage() throws Exception {
        return null;
    }

    @Override
    public int getOrientationOfCamera() {
        return 0;
    }
}
