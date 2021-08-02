package com.appliedrec.verid.sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;

public class RegistrationImportReviewActivityResultContract extends ActivityResultContract<Intent, Integer> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Intent input) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setDataAndType(input.getData(), "application/verid-registration");
        intent.putExtras(input);
        return intent;
    }

    @Override
    public Integer parseResult(int resultCode, @Nullable Intent intent) {
        if (resultCode == Activity.RESULT_OK && intent != null && intent.getIntExtra(RegistrationImportReviewActivity.EXTRA_IMPORTED_FACE_COUNT, 0) > 0) {
            return intent.getIntExtra(RegistrationImportReviewActivity.EXTRA_IMPORTED_FACE_COUNT, 0);
        }
        return null;
    }
}
