package com.appliedrec.verid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

/**
 * Shows an error message dialog.
 */
class CameraPermissionErrorDialog extends DialogFragment {

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

    @Override
    @Nullable
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
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> activity.finish())
                .create();
    }

}
