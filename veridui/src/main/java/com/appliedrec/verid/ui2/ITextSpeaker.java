package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;

import java.util.Locale;

/**
 * Interface for speech synthesis
 * @since 2.0.0
 */
@Keep
public interface ITextSpeaker {

    /**
     * Speak the given text
     * @param text Text to speak
     * @param locale Locale of the text to be spoken
     * @param interrupt {@literal true} to interrupt any speech that may be in progress or {@literal false} to let previous speech finish before speaking
     * @since 2.0.0
     */
    @Keep
    void speak(String text, Locale locale, boolean interrupt);
}
