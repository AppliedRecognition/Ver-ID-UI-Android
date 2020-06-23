package com.appliedrec.verid.ui2;

import android.os.Bundle;

import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.ui2.databinding.ActivityFailureBinding;

public class SessionFailureActivity extends AbstractSessionFailureActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityFailureBinding viewBinding = ActivityFailureBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        getSessionResult().getError().ifPresent(error -> {
            if (error.getCode() == VerIDSessionException.Code.TIMEOUT) {
                viewBinding.errorTextView.setText(translate("Session timed out"));
            } else {
                viewBinding.errorTextView.setText(translate("Session failed"));
            }
        });
    }

}
