package com.appliedrec.verid.ui2;

/**
 * Ver-ID UI session exception
 * @since 2.0.0
 */
public class VerIDUISessionException extends Exception {

    /**
     * Error codes
     * @since 2.0.0
     */
    public enum Code {
        /**
         * Thrown when attempting to start a session that already started
         */
        SESSION_ALREADY_STARTED,
        /**
         * Thrown when attempting to start a session with an instance of {@link com.appliedrec.verid.core2.VerID} whose context is no longer available
         */
        CONTEXT_UNAVAILABLE
    }

    private final Code code;

    /**
     * Constructor
     * @param code Error code
     * @since 2.0.0
     */
    public VerIDUISessionException(Code code) {
        this.code = code;
    }

    /**
     * Get error code
     * @return Error code
     * @since 2.0.0
     */
    public Code getCode() {
        return code;
    }
}
