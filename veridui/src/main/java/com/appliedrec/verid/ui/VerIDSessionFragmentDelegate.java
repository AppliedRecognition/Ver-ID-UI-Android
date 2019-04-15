package com.appliedrec.verid.ui;

import com.appliedrec.verid.core.VerIDSessionSettings;

public interface VerIDSessionFragmentDelegate {
    VerIDSessionSettings getSessionSettings();
    void veridSessionFragmentDidFailWithError(IVerIDSessionFragment fragment, Exception error);
}
