package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.Optional;

/**
 * Ver-ID session interface
 * @since 2.0.0
 */
@Keep
public interface IVerIDSession<T extends VerIDSessionInViewDelegate> {

    /**
     * @return Unique identifier for the session
     * @since 2.0.0
     */
    @Keep
    long getSessionIdentifier();

    /**
     * Start the session
     * @since 2.0.0
     */
    @Keep
    void start();

    /**
     * @return Session delegate
     * @since 2.0.0
     */
    @Keep
    Optional<T> getDelegate();

    /**
     * Set session delegate
     * @param delegate Session delegate
     * @since 2.0.0
     */
    @Keep
    void setDelegate(T delegate);

    /**
     * @return Instance of {@link VerID} the session uses for face detection and recognition
     * @since 2.0.0
     */
    @Keep
    VerID getVerID();

    /**
     * @return Session settings
     * @since 2.0.0
     */
    @Keep
    VerIDSessionSettings getSettings();
}
