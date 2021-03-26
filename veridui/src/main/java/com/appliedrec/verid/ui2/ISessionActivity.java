package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;

/**
 * Session activity interface
 * @since 2.0.0
 */
@Keep
public interface ISessionActivity {

    /**
     * Set session parameters
     * @param sessionParameters Session parameters
     * @since 2.0.0
     */
    @Keep
    void setSessionParameters(SessionParameters sessionParameters);
}
