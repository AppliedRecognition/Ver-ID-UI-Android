package com.appliedrec.verid.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.SessionSettings;

/**
 * Creates a dialog that shows an animation of a face responding to liveness detection prompts
 * @since 1.0.0
 */
public class SessionFailureDialogFactory implements ISessionFailureDialogFactory {

    enum ScreenDensity {
        MEDIUM, HIGH, EXTRA_HIGH
    }

    /**
     * Make a dialog that shows an animation of a face responding to liveness detection prompts.
     * @param activity Activity that will be presenting the dialog
     * @param message Message in the dialog
     * @param listener Dialog listener
     * @param sessionSettings Session settings
     * @return Alert dialog
     * @since 1.0.0
     */
    @Override
    public AlertDialog makeDialog(Activity activity, String message, final SessionFailureDialogListener listener, SessionSettings sessionSettings) {
        ScreenDensity screenDensity;
        float density = activity.getResources().getDisplayMetrics().density;
        if (density > 2) {
            screenDensity = ScreenDensity.EXTRA_HIGH;
        } else if (density > 1) {
            screenDensity = ScreenDensity.HIGH;
        } else {
            screenDensity = ScreenDensity.MEDIUM;
        }
        final MediaPlayer mediaPlayer = MediaPlayer.create(activity, getVideoResourceId(screenDensity, sessionSettings));
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mediaPlayer.start();
            }
        });
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
        AlertDialog dialog = new AlertDialog.Builder(activity).
                setMessage(message).
                setView(frameLayout).
                setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onCancel();
                        }
                    }
                }).
                setNeutralButton(R.string.tips, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onShowTips();
                        }
                    }
                }).
                setPositiveButton(R.string.try_again, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onRetry();
                        }
                    }
                }).create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    int getVideoResourceId(ScreenDensity screenDensity, SessionSettings sessionSettings) {
        switch (screenDensity) {
            case MEDIUM:
                if (sessionSettings instanceof RegistrationSessionSettings) {
                    return R.raw.registration_1;
                } else {
                    return R.raw.liveness_detection_1;
                }
            case HIGH:
                if (sessionSettings instanceof RegistrationSessionSettings) {
                    return R.raw.registration_2;
                } else {
                    return R.raw.liveness_detection_2;
                }
            case EXTRA_HIGH:
                if (sessionSettings instanceof RegistrationSessionSettings) {
                    return R.raw.registration_3;
                } else {
                    return R.raw.liveness_detection_3;
                }
        }
        return -1;
    }
}
