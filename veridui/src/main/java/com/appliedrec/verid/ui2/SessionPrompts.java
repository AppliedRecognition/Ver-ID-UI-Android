package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appliedrec.verid.core2.session.FaceDetectionResult;

import java.util.Locale;
import java.util.Optional;

/**
 * Prompts displayed above the detected face in camera preview
 * @since 2.0.0
 */
@Keep
public class SessionPrompts {

    private final IStringTranslator stringTranslator;

    /**
     * Constructor
     * @param stringTranslator Translator used to translate the prompts
     * @since 2.0.0
     */
    @Keep
    public SessionPrompts(@NonNull IStringTranslator stringTranslator) {
        this.stringTranslator = stringTranslator;
    }

    /**
     * Get a prompt from a face detection result
     * @param faceDetectionResult Face detetion result
     * @return Optional whose unwrapped value is a prompt or {@literal null} if the face detection result is {@literal null} or if the prompt shouldn't be displayed for the result
     * @since 2.0.0
     */
    @Keep
    public Optional<String> promptFromFaceDetectionResult(@Nullable FaceDetectionResult faceDetectionResult) {
        if (faceDetectionResult == null) {
            return Optional.empty();
        }
        switch (faceDetectionResult.getStatus()) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return Optional.of(stringTranslator.getTranslatedString("Great, hold it"));
            case FACE_MISALIGNED:
                return Optional.of(stringTranslator.getTranslatedString("Slowly turn to follow the arrow"));
            case FACE_TURNED_TOO_FAR:
                return Optional.empty();
            default:
                return Optional.of(stringTranslator.getTranslatedString("Align your face with the oval"));
        }
    }

    /**
     * Get locale from the string translator
     * @return Locale
     * @since 2.0.0
     */
    public Locale getLocale() {
        return stringTranslator.getLocale();
    }
}
