package com.appliedrec.verid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * Shows an error message dialog.
 */
public class CameraPermissionErrorDialog extends DialogFragment {

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
                .setMessage(translator.getTranslatedString("Camera permission is required"))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        activity.finish();
                    }
                })
                .create();
    }

}
