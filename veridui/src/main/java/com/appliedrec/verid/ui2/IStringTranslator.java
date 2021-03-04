package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;

/**
 * Interface for string translations
 * @since 2.0.0
 */
@Keep
@SuppressWarnings("WeakerAccess")
public interface IStringTranslator extends ILocaleProvider {

    /**
     * Translate a string
     * @param original Original string
     * @param args Additional string format arguments
     * @return Translated string
     * @since 2.0.0
     */
    @Keep
    String getTranslatedString(String original, Object ...args);
}
