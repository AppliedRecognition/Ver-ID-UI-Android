package com.appliedrec.verid.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core.DetectedFace;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.RecognizableFace;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.io.File;

public class VerIDSessionProxyActivity<T extends VerIDSessionSettings> extends AppCompatActivity {

    private static final int REQUEST_CODE = 0;

    static final String EXTRA_SESSION_ID = "com.appliedrec.verid.EXTRA_SESSION_ID";

    private int sessionId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            sessionId = getIntent().getIntExtra(EXTRA_SESSION_ID, -1);
            try {
                int veridInstanceId = getIntent().getIntExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, -1);
                VerID verid = VerID.getInstance(veridInstanceId);
                T settings = getIntent().getParcelableExtra(VerIDSessionActivity.EXTRA_SETTINGS);
                Intent intent = new VerIDSessionIntent<>(this, verid, settings);
                startActivityForResult(intent, REQUEST_CODE);
            } catch (Exception e) {
                VerIDSession session = VerIDSession.sessions.get(sessionId);
                if (session != null) {
                    //noinspection unchecked
                    session.onSessionFinished(new VerIDSessionResult(new Exception(), Face.class));
                }
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            VerIDSession session = VerIDSession.sessions.get(sessionId);
            if (session != null) {
                if (resultCode == RESULT_CANCELED) {
                    session.onSessionCancelled();
                } else {
                    com.appliedrec.verid.core.VerIDSessionResult coreResult = null;
                    if (data != null) {
                        coreResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
                    }
                    if (coreResult == null) {
                        coreResult = new com.appliedrec.verid.core.VerIDSessionResult(new Exception());
                    }
                    VerIDSessionResult result;
                    if (session.getSettings().getIncludeFaceTemplatesInResult()) {
                        result = new VerIDSessionResult<>(coreResult, RecognizableFace.class);
                    } else {
                        result = new VerIDSessionResult<>(coreResult, Face.class);
                    }
                    try {
                        for (DetectedFace detectedFace : coreResult.getAttachments()) {
                            if (detectedFace.getImageUri() != null) {
                                String imagePath = detectedFace.getImageUri().getPath();
                                if (imagePath != null) {
                                    File imageFile = new File(imagePath);
                                    //noinspection ResultOfMethodCallIgnored
                                    imageFile.delete();
                                }
                            }
                        }
                    } catch (Exception ignore) {}
                    session.onSessionFinished(result);
                }
            }
        }
        finish();
    }
}
