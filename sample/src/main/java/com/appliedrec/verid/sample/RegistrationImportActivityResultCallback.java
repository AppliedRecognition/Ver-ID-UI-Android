package com.appliedrec.verid.sample;

import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;

import static android.app.Activity.RESULT_OK;

public class RegistrationImportActivityResultCallback implements ActivityResultCallback<ActivityResult> {

    private final ActivityResultLauncher<Intent> reviewLauncher;

    public RegistrationImportActivityResultCallback(ActivityResultLauncher<Intent> reviewLauncher) {
        this.reviewLauncher = reviewLauncher;
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
            reviewLauncher.launch(result.getData());
        }
    }
}
