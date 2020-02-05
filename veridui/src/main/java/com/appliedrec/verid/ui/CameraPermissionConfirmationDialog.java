package com.appliedrec.verid.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

/**
 * Shows OK/Cancel confirmation dialog about camera permission.
 */
public class CameraPermissionConfirmationDialog extends DialogFragment {

    private IStringTranslator translator;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof IStringTranslator) {
            translator = (IStringTranslator) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        translator = null;
    }

    @Nullable
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        if (translator == null) {
            translator = new TranslatedStrings(activity, null);
        }
        return new AlertDialog.Builder(activity)
                .setMessage(translator.getTranslatedString("Camera used for face authentication"))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, VerIDSessionActivity.REQUEST_CAMERA_PERMISSION))
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> {
                            if (activity != null) {
                                activity.finish();
                            }
                        })
                .create();
    }
}
