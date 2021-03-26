package com.appliedrec.verid.ui2;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core2.session.VerIDSessionResult;

public abstract class SessionResultActivity extends AppCompatActivity implements ISessionActivity {

    public static final String EXTRA_TRANSLATOR = "com.appliedrec.verid.EXTRA_TRANSLATOR";

    private IStringTranslator stringTranslator;
    private VerIDSessionResult sessionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().hasExtra(EXTRA_TRANSLATOR)) {
            stringTranslator = getIntent().getParcelableExtra(EXTRA_TRANSLATOR);
        }
    }

    @Override
    public void setSessionParameters(SessionParameters sessionParameters) {
        sessionResult = sessionParameters.getSessionResult().orElseThrow(RuntimeException::new);
        stringTranslator = sessionParameters.getStringTranslator();
    }

    VerIDSessionResult getSessionResult() {
        return sessionResult;
    }

    String translate(String original, Object... args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        }
        return String.format(original, args);
    }
}
