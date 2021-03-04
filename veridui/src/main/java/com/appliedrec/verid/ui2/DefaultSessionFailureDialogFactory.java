package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import com.appliedrec.verid.core2.AntiSpoofingException;
import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.FacePresenceException;
import com.appliedrec.verid.core2.session.VerIDSessionException;

/**
 * Ver-ID's default implementation of the {@link SessionFailureDialogFactory session failure dialog factory}
 * @since 2.0.0
 */
@Keep
public class DefaultSessionFailureDialogFactory implements SessionFailureDialogFactory {

    enum ScreenDensity {
        MEDIUM, HIGH, EXTRA_HIGH
    }

    @Keep
    @Override
    public <T extends Activity & ISessionActivity> AlertDialog makeDialog(@NonNull T activity, @NonNull Consumer<OnDismissAction> onDismissListener, @NonNull VerIDSessionException exception, @NonNull IStringTranslator stringTranslator) {
        ScreenDensity screenDensity;
        float density = activity.getResources().getDisplayMetrics().density;
        if (density > 2) {
            screenDensity = ScreenDensity.EXTRA_HIGH;
        } else if (density > 1) {
            screenDensity = ScreenDensity.HIGH;
        } else {
            screenDensity = ScreenDensity.MEDIUM;
        }
        Bearing requestedBearing = null;
        String message = null;
        Integer videoResourceId = null;
        if (exception.getCode() == VerIDSessionException.Code.FACE_IS_COVERED) {
            message = stringTranslator.getTranslatedString("Please remove face coverings");
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
        } else if (exception.getCause() instanceof AntiSpoofingException) {
            AntiSpoofingException antiSpoofingException = (AntiSpoofingException)exception.getCause();
            requestedBearing = antiSpoofingException.getRequestedBearing();
            if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_OPPOSITE) {
                message = stringTranslator.getTranslatedString("Turn your head in the direction of the arrow");
            } else if (antiSpoofingException.getCode() == AntiSpoofingException.Code.MOVED_TOO_FAST) {
                message = stringTranslator.getTranslatedString("Please turn slowly");
            }
        } else if (exception.getCause() instanceof FacePresenceException) {
            FacePresenceException facePresenceException = (FacePresenceException)exception.getCause();
            requestedBearing = facePresenceException.getRequestedBearing();
            if (facePresenceException.getCode() == FacePresenceException.Code.FACE_MOVED_TOO_FAR) {
                message = stringTranslator.getTranslatedString("You may have turned too far");
            } else if (facePresenceException.getCode() == FacePresenceException.Code.FACE_LOST) {
                message = stringTranslator.getTranslatedString("Turn your head in the direction of the arrow");
            }
        } else {
            return null;
        }
        if (videoResourceId == null && requestedBearing != null) {
            videoResourceId = getVideoResourceId(screenDensity, requestedBearing);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message);
        if (videoResourceId != null) {
            final MediaPlayer mediaPlayer = MediaPlayer.create(activity, videoResourceId);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> false);
            mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());
            mediaPlayer.setLooping(true);
            mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);

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
            builder.setView(frameLayout);
        }
        String cancel = stringTranslator.getTranslatedString("Cancel");
        String tips = stringTranslator.getTranslatedString("Tips");
        String tryAgain = stringTranslator.getTranslatedString("Try again");
        builder.setNegativeButton(cancel, (dialogInterface, which) -> onDismissListener.accept(OnDismissAction.CANCEL));
        builder.setNeutralButton(tips, (dialogInterface, which) -> onDismissListener.accept(OnDismissAction.SHOW_TIPS));
        builder.setPositiveButton(tryAgain, (dialogInterface, which) -> onDismissListener.accept(OnDismissAction.RETRY));
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @RawRes
    private Integer getVideoResourceId(ScreenDensity screenDensity, Bearing requestedBearing) {
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
        return null;
    }
}
