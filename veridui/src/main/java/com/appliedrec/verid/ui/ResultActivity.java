package com.appliedrec.verid.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.OperationCanceledException;

public abstract class ResultActivity extends AppCompatActivity implements IOnDoneListenerSettable, ITranslationSettable {

    private IOnDoneListener onDoneListener;
    private TranslatedStrings translatedStrings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (translatedStrings != null) {
            setTranslatedStrings(translatedStrings);
        }
    }

    @Override
    public void setOnDoneListener(@Nullable IOnDoneListener onDoneListener) {
        this.onDoneListener = onDoneListener;
    }

    @Override
    public void setTranslatedStrings(TranslatedStrings translatedStrings) {
        this.translatedStrings = translatedStrings;
    }

    protected void onDone(View view) {
        if (onDoneListener != null) {
            onDoneListener.onDone(this);
        } else {
            finish();
        }
    }

    protected boolean shouldCancelOnBackPress() {
        return false;
    }

    @Override
    public void onBackPressed() {
        if (onDoneListener != null) {
            if (shouldCancelOnBackPress()) {
                getIntent().putExtra(VerIDSession.EXTRA_ERROR, new OperationCanceledException());
            }
            onDoneListener.onDone(this);
        } else {
            super.onBackPressed();
        }
    }
}
