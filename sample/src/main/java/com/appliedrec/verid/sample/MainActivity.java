package com.appliedrec.verid.sample;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.ui.VerIDSessionActivity;

public class MainActivity extends AppCompatActivity implements VerIDFactoryDelegate {

    private static final int REQUEST_CODE_LIVENESS_DETECTION = 0;

    private Button button;
    private ProgressBar progressBar;
    private VerID environment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = findViewById(R.id.button);
        progressBar = findViewById(R.id.progressBar);
        VerIDFactory verIDFactory = new VerIDFactory(this, this);
        verIDFactory.createVerID();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION && resultCode == RESULT_OK) {

        }
    }

    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
        environment = verID;
        button.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
        progressBar.setVisibility(View.GONE);
    }

    public void startLivenessDetectionSession(View v) {
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        sessionSettings.setNumberOfResultsToCollect(2);
        sessionSettings.setIncludeFaceTemplatesInResult(true);
        sessionSettings.setShowResult(true);
        Intent intent = new Intent(this, VerIDSessionActivity.class);
        intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, sessionSettings);
        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, environment.getInstanceId());
        startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
    }
}
