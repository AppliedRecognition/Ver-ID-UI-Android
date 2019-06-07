package com.appliedrec.verid.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

/**
 * Shows OK/Cancel confirmation dialog about camera permission.
 */
public class CameraPermissionConfirmationDialog extends DialogFragment {

    IStringTranslator translator;

    @Override
    public void onAttach(Context context) {
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
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, VerIDSessionActivity.REQUEST_CAMERA_PERMISSION);
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (activity != null) {
                                    activity.finish();
                                }
                            }
                        })
                .create();
    }
}
