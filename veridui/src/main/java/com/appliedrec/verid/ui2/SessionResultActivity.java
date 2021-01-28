package com.appliedrec.verid.ui2;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core2.session.VerIDSessionResult;

public abstract class SessionResultActivity extends AppCompatActivity implements ISessionResultActivity {

    public static final String EXTRA_TRANSLATOR = "com.appliedrec.verid.EXTRA_TRANSLATOR";

    private IStringTranslator stringTranslator;
    private VerIDSessionResult sessionResult;
    private boolean didCancel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().hasExtra(EXTRA_TRANSLATOR)) {
            stringTranslator = getIntent().getParcelableExtra(EXTRA_TRANSLATOR);
        }
    }

    @Override
    public void setSessionResult(VerIDSessionResult sessionResult) {
        this.sessionResult = sessionResult;
    }

    VerIDSessionResult getSessionResult() {
        return sessionResult;
    }

    @Override
    public void setTranslator(IStringTranslator stringTranslator) {
        this.stringTranslator = stringTranslator;
    }

    String translate(String original, Object... args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        }
        return String.format(original, args);
    }

    @Override
    public void onBackPressed() {
        didCancel = true;
        super.onBackPressed();
    }

    @Override
    public boolean didCancelSession() {
        return didCancel;
    }
}
