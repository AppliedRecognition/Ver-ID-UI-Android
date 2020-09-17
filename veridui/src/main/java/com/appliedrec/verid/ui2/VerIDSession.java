package com.appliedrec.verid.ui2;

import android.os.Build;

import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.AntiSpoofingException;
import com.appliedrec.verid.core2.FacePresenceException;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ver-ID session that uses {@link SessionActivityCameraX} and {@link SessionResultActivity}
 *
 * <h3>Example: Running a liveness detection session</h3>
 * <pre>
 * {@code
 * VerID verID; // Obtained from VerIDFactory
 * LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
 * VerIDSession veridSession = new VerIDSession(verID,  settings);
 * veridSession.setDelegate((session, result) -> {
 *     // Session finished
 *     if (result.getError().isPresent()) {
 *         // Session failed
 *     } else {
 *         // Session succeeded
 *     }
 * });
 * veridSession.start();
 * }
 * </pre>
 *
 * @param <Settings>
 * @since 2.0.0
 */
public class VerIDSession<Settings extends VerIDSessionSettings> extends AbstractVerIDSession<Settings, AbstractSessionActivity<?>, SessionResultActivity> {

    private AtomicBoolean useCameraX = new AtomicBoolean(true);
    private AtomicBoolean preferSurfaceView = new AtomicBoolean(false);

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @since 2.0.0
     */
    public VerIDSession(@NonNull VerID verID, @NonNull Settings settings) {
        super(verID, settings);
    }

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @param stringTranslator Translator for strings used in the session
     * @since 2.0.0
     */
    public VerIDSession(@NonNull VerID verID, @NonNull Settings settings, @NonNull IStringTranslator stringTranslator) {
        super(verID, settings, stringTranslator);
    }

    public void setUseCameraX(boolean useCameraX) {
        this.useCameraX.set(useCameraX);
    }

    public boolean getUseCameraX() {
        return useCameraX.get();
    }

    public boolean getPreferSurfaceView() {
        return preferSurfaceView.get();
    }

    public void setPreferSurfaceView(boolean preferSurfaceView) {
        this.preferSurfaceView.set(preferSurfaceView);
    }

    @NonNull
    @Override
    protected Class<? extends AbstractSessionActivity<?>> getSessionActivityClass() {
        if (useCameraX.get()) {
            return SessionActivityCameraX.class;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preferSurfaceView.get()) {
            return SessionActivity.class;
        } else {
            return SessionActivityWithTextureView.class;
        }
    }

    @NonNull
    @Override
    protected Class<? extends SessionResultActivity> getSessionResultActivityClass(@NonNull VerIDSessionResult sessionResult) {
        if (!sessionResult.getError().isPresent()) {
            return SessionSuccessActivity.class;
        } else {
            VerIDSessionException error = sessionResult.getError().get();
            if (error.getCode() == VerIDSessionException.Code.LIVENESS_FAILURE && error.getCause() != null && (error.getCause() instanceof AntiSpoofingException || error.getCause() instanceof FacePresenceException) && getRunCount() <= getSettings().getMaxRetryCount()) {
                return SessionLivenessDetectionFailureActivity.class;
            } else {
                return SessionFailureActivity.class;
            }
        }
    }
}
