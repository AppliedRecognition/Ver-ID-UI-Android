package com.appliedrec.verid.ui;

import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceCapture;
import com.appliedrec.verid.core.VerIDSessionSettings;

public interface VerIDSessionDelegate<T extends Face, U extends VerIDSessionSettings<T>> {

    void onSessionFinished(VerIDSession<T,U> session, FaceCapture<T>[] faceCaptures);

    void onSessionFailed(VerIDSession<T,U> session, Throwable error);
}
