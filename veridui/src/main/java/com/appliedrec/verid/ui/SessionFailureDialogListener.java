package com.appliedrec.verid.ui;

/**
 * Session failure dialog listener
 * @since 1.0.0
 */
public interface SessionFailureDialogListener {
    /**
     * Called when the user wants to cancel the session.
     * @since 1.0.0
     */
    void onCancel();

    /**
     * Called when the user wants to see tips on how to use Ver-ID.
     * @since 1.0.0
     */
    void onShowTips();

    /**
     * Called when the user wants to retry the session.
     * @since 1.0.0
     */
    void onRetry();
}
