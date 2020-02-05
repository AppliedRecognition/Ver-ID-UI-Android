package com.appliedrec.verid.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;

/**
 * Creates a dialog that shows an animation of a face responding to liveness detection prompts
 * @since 1.0.0
 */
public class SessionFailureDialogFactory implements ISessionFailureDialogFactory2 {

    enum ScreenDensity {
        MEDIUM, HIGH, EXTRA_HIGH
    }

    /**
     * Make a dialog that shows an animation of a face responding to liveness detection prompts.
     * @param activity Activity that will be presenting the dialog
     * @param message Message in the dialog
     * @param listener Dialog listener
     * @param sessionSettings Session settings
     * @param faceDetectionResult Latest face detection result
     * @return Alert dialog
     * @since 1.12.0
     */
    @Override
    public AlertDialog makeDialog(Activity activity, String message, final SessionFailureDialogListener listener, VerIDSessionSettings sessionSettings, FaceDetectionResult faceDetectionResult) {
        ScreenDensity screenDensity;
        float density = activity.getResources().getDisplayMetrics().density;
        if (density > 2) {
            screenDensity = ScreenDensity.EXTRA_HIGH;
        } else if (density > 1) {
            screenDensity = ScreenDensity.HIGH;
        } else {
            screenDensity = ScreenDensity.MEDIUM;
        }
        final MediaPlayer mediaPlayer = MediaPlayer.create(activity, getVideoResourceId(screenDensity, faceDetectionResult));
        mediaPlayer.setOnErrorListener((mp, what, extra) -> false);
        mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());
        mediaPlayer.setLooping(true);
        if (Build.VERSION.SDK_INT >= 16) {
            mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        }

        TextureView textureView = new TextureView(activity);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Surface s = new Surface(surface);
                mediaPlayer.setSurface(s);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        FixedAspectRatioFrameLayout frameLayout = new FixedAspectRatioFrameLayout(activity, 4, 3);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        frameLayout.addView(textureView, layoutParams);
        String cancel = "Cancel";
        String tips = "Tips";
        String tryAgain = "Try again";
        if (activity instanceof IStringTranslator) {
            IStringTranslator translator = (IStringTranslator) activity;
            cancel = translator.getTranslatedString("Cancel");
            tips = translator.getTranslatedString("Tips");
            tryAgain = translator.getTranslatedString("Try again");
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).
                setMessage(message).
                setView(frameLayout).
                setNegativeButton(cancel, (dialog1, which) -> {
                    if (listener != null) {
                        listener.onCancel();
                    }
                }).
                setNeutralButton(tips, (dialog12, which) -> {
                    if (listener != null) {
                        listener.onShowTips();
                    }
                }).
                setPositiveButton(tryAgain, (dialog13, which) -> {
                    if (listener != null) {
                        listener.onRetry();
                    }
                }).
                setCancelable(false).
                create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private int getVideoResourceId(ScreenDensity screenDensity, FaceDetectionResult faceDetectionResult) {
        switch (screenDensity) {
            case MEDIUM:
                switch (faceDetectionResult.getRequestedBearing()) {
                    case STRAIGHT:
                        return R.raw.up_to_centre_1;
                    case UP:
                        return R.raw.up_1;
                    case LEFT_UP:
                        return R.raw.right_up_1;
                    case LEFT:
                        return R.raw.right_1;
                    case LEFT_DOWN:
                        return R.raw.right_down_1;
                    case DOWN:
                        return R.raw.down_1;
                    case RIGHT_DOWN:
                        return R.raw.left_down_1;
                    case RIGHT:
                        return R.raw.left_1;
                    case RIGHT_UP:
                        return R.raw.left_up_1;
                }
            case HIGH:
                switch (faceDetectionResult.getRequestedBearing()) {
                    case STRAIGHT:
                        return R.raw.up_to_centre_2;
                    case UP:
                        return R.raw.up_2;
                    case LEFT_UP:
                        return R.raw.right_up_2;
                    case LEFT:
                        return R.raw.right_2;
                    case LEFT_DOWN:
                        return R.raw.right_down_2;
                    case DOWN:
                        return R.raw.down_2;
                    case RIGHT_DOWN:
                        return R.raw.left_down_2;
                    case RIGHT:
                        return R.raw.left_2;
                    case RIGHT_UP:
                        return R.raw.left_up_2;
                }
            case EXTRA_HIGH:
                switch (faceDetectionResult.getRequestedBearing()) {
                    case STRAIGHT:
                        return R.raw.up_to_centre_3;
                    case UP:
                        return R.raw.up_3;
                    case LEFT_UP:
                        return R.raw.right_up_3;
                    case LEFT:
                        return R.raw.right_3;
                    case LEFT_DOWN:
                        return R.raw.right_down_3;
                    case DOWN:
                        return R.raw.down_3;
                    case RIGHT_DOWN:
                        return R.raw.left_down_3;
                    case RIGHT:
                        return R.raw.left_3;
                    case RIGHT_UP:
                        return R.raw.left_up_3;
                }
        }
        return -1;
    }
}
