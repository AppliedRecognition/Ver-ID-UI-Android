package com.appliedrec.verid.sample.sharing;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;

import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.sample.IVerIDLoadObserver;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.R;
import com.appliedrec.verid.sample.sharing.RegistrationData;
import com.appliedrec.verid.sample.sharing.RegistrationImport;

/**
 * Activity that displays downloaded profile picture and registers downloaded face templates
 */
public class RegistrationImportReviewActivity extends AppCompatActivity implements IVerIDLoadObserver {

    public static final String EXTRA_IMPORTED_FACE_COUNT = "importedFaceCount";
    private ProfilePhotoHelper profilePhotoHelper;
    private RegistrationData registrationData;
    private RegistrationImport registrationImport;
    private VerID verID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        profilePhotoHelper = new ProfilePhotoHelper(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_import_review);
        Group registration = findViewById(R.id.registration);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        registration.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        CheckBox checkBox = findViewById(R.id.checkBox);
        TextView textView = findViewById(R.id.textView);
        Button importButton = findViewById(R.id.button);
        ImageView imageView = findViewById(R.id.imageView);
        importButton.setEnabled(false);
        importButton.setOnClickListener(view -> AsyncTask.execute(() -> {
            try {
                if (registrationImport == null || registrationData == null) {
                    return;
                }
                registrationImport.importRegistration(registrationData);
                runOnUiThread(this::onImported);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> onFailure(e));
            }
        }));
        if (getIntent() != null && (getIntent().hasExtra(Intent.EXTRA_STREAM) || getIntent().getData() != null)) {
            AsyncTask.execute(() -> {
                try {
                    if (registrationImport == null || verID == null) {
                        return;
                    }
                    String[] users = verID.getUserManagement().getUsers();
                    Uri registrationUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
                    if (registrationUri == null) {
                        registrationUri = getIntent().getData();
                    }
                    if (registrationUri == null) {
                        return;
                    }
                    registrationData = registrationImport.registrationDataFromUri(this, registrationUri);
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        imageView.setImageBitmap(registrationData.getProfilePicture());
                        importButton.setEnabled(true);
                        registration.setVisibility(View.VISIBLE);
                        if (users.length == 0) {
                            // For some reason setting visibility to GONE still keeps the checkbox visible
                            checkBox.getLayoutParams().height = 1;
                            checkBox.setAlpha(0);
                        }
                        progressBar.setVisibility(View.GONE);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        progressBar.setVisibility(View.GONE);
                        registration.setVisibility(View.VISIBLE);
                        importButton.setVisibility(View.GONE);
                        checkBox.setVisibility(View.GONE);
                        imageView.setVisibility(View.GONE);
                        textView.setText(R.string.failed_to_import_registration);
                    });
                }
            });
        }
    }

    private void onImported() {
        if (isDestroyed()) {
            return;
        }
        Intent data = new Intent();
        if (registrationData != null) {
            data.putExtra(EXTRA_IMPORTED_FACE_COUNT, registrationData.getFaceTemplates().length);
        }
        setResult(RESULT_OK, data);
        finish();
    }

    private void onFailure(Throwable throwable) {
        if (isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.failed_to_import_registration)
                .setMessage(throwable.getLocalizedMessage())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .create()
                .show();
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        registrationImport = new RegistrationImport(verid, profilePhotoHelper);
    }

    @Override
    public void onVerIDUnloaded() {
        this.verID = null;
    }
}
