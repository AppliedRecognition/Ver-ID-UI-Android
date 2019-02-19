package com.appliedrec.verid.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * Shows OK/Cancel confirmation dialog about camera permission.
 */
public class CameraPermissionConfirmationDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        return new AlertDialog.Builder(activity)
                .setMessage(R.string.access_to_camera)
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
