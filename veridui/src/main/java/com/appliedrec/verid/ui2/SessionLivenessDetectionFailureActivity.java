package com.appliedrec.verid.ui2;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.appliedrec.verid.core2.AntiSpoofingException;
import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.FacePresenceException;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.ui2.databinding.ActivityResultErrorBinding;

public class SessionLivenessDetectionFailureActivity extends AbstractSessionFailureActivity {

    @Override
    public boolean didTapRetryButtonInSessionResultActivity() {
        return shouldRetry;
    }

    enum ScreenDensity {
        MEDIUM, HIGH, EXTRA_HIGH
    }

    private MediaPlayer mediaPlayer;
    private boolean shouldRetry = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.appliedrec.verid.ui2.databinding.ActivityResultErrorBinding activityResultErrorBinding = ActivityResultErrorBinding.inflate(getLayoutInflater());
        setContentView(activityResultErrorBinding.getRoot());
        if (!getSessionResult().getError().isPresent()) {
            finish();
            return;
        }
        VerIDSessionException exception = getSessionResult().getError().get();
        float density = getResources().getDisplayMetrics().density;
        ScreenDensity screenDensity;
        if (density > 2) {
            screenDensity = ScreenDensity.EXTRA_HIGH;
        } else if (density > 1) {
            screenDensity = ScreenDensity.HIGH;
        } else {
            screenDensity = ScreenDensity.MEDIUM;
        }
        Bearing requestedBearing = null;
        String message = null;
        int videoResourceId = -1;
        if (exception instanceof VerIDSessionException) {
            VerIDSessionException sessionException = (VerIDSessionException)exception;
            if (sessionException.getCode() == VerIDSessionException.Code.FACE_IS_COVERED) {
                message = translate("Please remove face coverings");
                switch (screenDensity) {
                    case EXTRA_HIGH:
                        videoResourceId = R.raw.face_mask_off_3;
                        break;
                    case HIGH:
                        videoResourceId = R.raw.face_mask_off_2;
                        break;
                    case MEDIUM:
                        videoResourceId = R.raw.face_mask_off_1;
                        break;
                }
            }
        } else if (exception.getCause() instanceof AntiSpoofingException) {
            AntiSpoofingException antiSpoofingException = (AntiSpoofingException)exception.getCause();
            requestedBearing = antiSpoofingException.getRequestedBearing();
            if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_OPPOSITE) {
                message = translate("Turn your head in the direction of the arrow");
            } else if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_TOO_FAST) {
                message = translate("Please turn slowly");
            }
        } else if (exception.getCause() instanceof FacePresenceException) {
            FacePresenceException facePresenceException = (FacePresenceException)exception.getCause();
            requestedBearing = facePresenceException.getRequestedBearing();
            if (facePresenceException.getCode() == FacePresenceException.Code.FACE_MOVED_TOO_FAR) {
                message = translate("You may have turned too far");
            } else if (facePresenceException.getCode() == FacePresenceException.Code.FACE_LOST) {
                message = translate("Turn your head in the direction of the arrow");
            }
        }
        if (videoResourceId == -1 && requestedBearing != null) {
            videoResourceId = getVideoResourceId(screenDensity, requestedBearing);
        }
        if (message != null) {
            activityResultErrorBinding.textView.setText(message);
        }
        activityResultErrorBinding.retryButton.setOnClickListener(v -> {
            shouldRetry = true;
            setResult(RESULT_OK);
            finish();
        });
        if (videoResourceId != -1) {
            mediaPlayer = MediaPlayer.create(this, videoResourceId);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> false);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
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
        intent.putExtra(AbstractSessionActivity.EXTRA_SESSION_ID, getIntent().getLongExtra(AbstractSessionActivity.EXTRA_SESSION_ID, -1));
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
