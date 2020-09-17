package com.appliedrec.verid.ui2;

import com.appliedrec.verid.core2.session.FaceDetectionResult;

import java.util.Locale;
import java.util.Optional;

public class SessionPrompts {

    private final IStringTranslator stringTranslator;

    public SessionPrompts(IStringTranslator stringTranslator) {
        this.stringTranslator = stringTranslator;
    }

    public Optional<String> promptFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
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

    public Locale getLocale() {
        return stringTranslator.getLocale();
    }
}
