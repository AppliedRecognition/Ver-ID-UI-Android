package com.appliedrec.verid.sample;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Constraints;

import android.os.Bundle;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivitySessionInSmallViewBinding;
import com.appliedrec.verid.ui2.SessionView;
import com.appliedrec.verid.ui2.TranslatedStrings;
import com.appliedrec.verid.ui2.VerIDSessionInView;

public class SessionInSmallViewActivity extends AppCompatActivity implements IVerIDLoadObserver {

    ActivitySessionInSmallViewBinding viewBinding;
    VerID verID;
    VerIDSessionInView<SessionView> verIDSessionInView;
    public static final String EXTRA_WIDTH = "com.appliedrec.verid.width";
    public static final String EXTRA_HEIGHT = "com.appliedrec.verid.height";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivitySessionInSmallViewBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        int previewWidth = getIntent().getIntExtra(EXTRA_WIDTH, 300);
        int previewHeight = getIntent().getIntExtra(EXTRA_HEIGHT, 300);
        float screenDensity = getResources().getDisplayMetrics().density;
        ConstraintLayout.LayoutParams layoutParams = new Constraints.LayoutParams((int)((float)previewWidth * screenDensity), (int)((float)previewHeight * screenDensity));
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        viewBinding.sessionView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        verIDSessionInView = new VerIDSessionInView<>(verID, sessionSettings, viewBinding.sessionView, new TranslatedStrings(this, null));
        verIDSessionInView.getSessionResultLiveData().observe(this, this::onSessionResult);
        startSession();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (verIDSessionInView != null) {
            verIDSessionInView.getSessionResultLiveData().removeObservers(this);
            verIDSessionInView.stop();
            verIDSessionInView = null;
        }
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
    }

    @Override
    public void onVerIDUnloaded() {
        this.verID = null;
    }

    private void startSession() {
        if (verIDSessionInView == null) {
            return;
        }
        verIDSessionInView.start();
    }

    private void onSessionResult(VerIDSessionResult sessionResult) {
        String title;
        String buttonLabel;
        if (sessionResult.getError().isPresent()) {
            title = "Session failed";
            buttonLabel = "Try again";
        } else {
            title = "Session succeeded";
            buttonLabel = "Run again";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setNeutralButton(buttonLabel, (view, dialog) -> startSession())
                .setPositiveButton("Close", (view, dialog) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .create()
                .show();
    }
}