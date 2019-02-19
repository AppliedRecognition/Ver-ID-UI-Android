package com.appliedrec.verid.sample;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.ui.VerIDSessionActivity;

public class MainActivity extends AppCompatActivity implements VerIDFactoryDelegate {

    private static final int REQUEST_CODE_LIVENESS_DETECTION = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        Intent intent = new Intent(this, VerIDSessionActivity.class);
        intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, sessionSettings);
        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
        startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {

    }
}
