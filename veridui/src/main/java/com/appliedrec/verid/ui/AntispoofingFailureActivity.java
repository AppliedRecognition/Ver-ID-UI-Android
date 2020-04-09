package com.appliedrec.verid.ui;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.appliedrec.verid.core.AntiSpoofingException;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.FacePresenceException;
import com.appliedrec.verid.ui.databinding.ActivityResultErrorBinding;

public class AntispoofingFailureActivity extends ResultActivity {

    enum ScreenDensity {
        MEDIUM, HIGH, EXTRA_HIGH
    }

    private Throwable error;
    private ActivityResultErrorBinding activityResultErrorBinding;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityResultErrorBinding = ActivityResultErrorBinding.inflate(getLayoutInflater());
        error = (Throwable) getIntent().getSerializableExtra(VerIDSession.EXTRA_ERROR);
        if (error == null) {
            finish();
            return;
        }
        activityResultErrorBinding.doneButton.setOnClickListener(this::onDone);
        activityResultErrorBinding.tipsButton.setOnClickListener(this::onShowTips);
        Bearing requestedBearing = null;
        if (error instanceof AntiSpoofingException) {
            AntiSpoofingException antiSpoofingException = (AntiSpoofingException)error;
            requestedBearing = antiSpoofingException.getRequestedBearing();
        } else if (error instanceof FacePresenceException) {
            FacePresenceException facePresenceException = (FacePresenceException)error;
            requestedBearing = facePresenceException.getRequestedBearing();
        }
        if (requestedBearing != null) {
            float density = getResources().getDisplayMetrics().density;
            ScreenDensity screenDensity;
            if (density > 2) {
                screenDensity = ScreenDensity.EXTRA_HIGH;
            } else if (density > 1) {
                screenDensity = ScreenDensity.HIGH;
            } else {
                screenDensity = ScreenDensity.MEDIUM;
            }
            int videoResourceId = getVideoResourceId(screenDensity, requestedBearing);
            mediaPlayer = MediaPlayer.create(this, videoResourceId);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> false);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setLooping(true);
            if (Build.VERSION.SDK_INT >= 16) {
                mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            }
            activityResultErrorBinding.videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                    mediaPlayer.setSurface(new Surface(surfaceTexture));
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            });
        }
        super.onCreate(savedInstanceState);
        setContentView(activityResultErrorBinding.getRoot());
    }

    @Override
    public void setTranslatedStrings(TranslatedStrings translatedStrings) {
        super.setTranslatedStrings(translatedStrings);
        if (activityResultErrorBinding != null) {
            String message = error.getLocalizedMessage();
            if (error instanceof AntiSpoofingException) {
                AntiSpoofingException antiSpoofingException = (AntiSpoofingException)error;
                if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_OPPOSITE) {
                    message = translatedStrings.getTranslatedString("Turn your head in the direction of the arrow");
                } else if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_TOO_FAST) {
                    message = translatedStrings.getTranslatedString("Please turn slowly");
                }
            } else if (error instanceof FacePresenceException) {
                FacePresenceException facePresenceException = (FacePresenceException)error;
                if (facePresenceException.getCode() == FacePresenceException.Code.FACE_MOVED_TOO_FAR) {
                    message = translatedStrings.getTranslatedString("You may have turned too far");
                } else if (facePresenceException.getCode() == FacePresenceException.Code.FACE_LOST) {
                    message = translatedStrings.getTranslatedString("Turn your head in the direction of the arrow");
                }
            }
            activityResultErrorBinding.doneButton.setText(translatedStrings.getTranslatedString("Done"));
            activityResultErrorBinding.tipsButton.setText(translatedStrings.getTranslatedString("Tips"));
            activityResultErrorBinding.textView.setText(message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void onShowTips(View view) {
        Intent intent = new Intent(this, TipsActivity.class);
        intent.putExtra(VerIDSession.EXTRA_SESSION_ID, getIntent().getLongExtra(VerIDSession.EXTRA_SESSION_ID, -1));
        startActivity(intent);
    }

    private int getVideoResourceId(ScreenDensity screenDensity, Bearing requestedBearing) {
        switch (screenDensity) {
            case MEDIUM:
                switch (requestedBearing) {
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
                switch (requestedBearing) {
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
                switch (requestedBearing) {
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
