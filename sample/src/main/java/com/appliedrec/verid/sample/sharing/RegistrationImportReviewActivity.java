package com.appliedrec.verid.sample.sharing;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.sample.IVerIDLoadObserver;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.R;
import com.appliedrec.verid.sample.databinding.ActivityRegistrationImportReviewBinding;

import java.util.ArrayList;
import java.util.Iterator;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Activity that displays downloaded profile picture and registers downloaded face templates
 */
public class RegistrationImportReviewActivity extends AppCompatActivity implements IVerIDLoadObserver {

    public static final String EXTRA_IMPORTED_FACE_COUNT = "importedFaceCount";
    private ProfilePhotoHelper profilePhotoHelper;
    private RegistrationData registrationData;
    private RegistrationImport registrationImport;
    private VerID verID;
    private ArrayList<Disposable> disposables = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        profilePhotoHelper = new ProfilePhotoHelper(this);
        super.onCreate(savedInstanceState);
        ActivityRegistrationImportReviewBinding viewBinding = ActivityRegistrationImportReviewBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewBinding.button.setEnabled(false);
        viewBinding.button.setOnClickListener(view -> {
            if (registrationImport == null || registrationData == null) {
                return;
            }
            disposables.add(registrationImport.importRegistration(registrationData)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            this::onImported,
                            this::onFailure
                    ));
        });
        if (getIntent() != null && (getIntent().hasExtra(Intent.EXTRA_STREAM) || getIntent().getData() != null)) {
            if (registrationImport == null || verID == null) {
                return;
            }
            Uri registrationUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM) != null ? getIntent().getParcelableExtra(Intent.EXTRA_STREAM) : getIntent().getData();
            if (registrationUri == null) {
                return;
            }
            disposables.add(verID.getUserManagement()
                    .getUsersSingle()
                    .flatMap(users -> registrationImport.registrationDataFromUri(registrationUri)
                            .map(regData -> new Pair<>(users,regData)))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            usersAndRegData -> {
                                registrationData = usersAndRegData.second;
                                viewBinding.imageView.setImageBitmap(registrationData.getProfilePicture());
                                viewBinding.button.setEnabled(true);
                                viewBinding.registration.setVisibility(View.VISIBLE);
                                if (usersAndRegData.first.length == 0) {
                                    // For some reason setting visibility to GONE still keeps the checkbox visible
                                    viewBinding.checkBox.getLayoutParams().height = 1;
                                    viewBinding.checkBox.setAlpha(0);
                                }
                                viewBinding.progressBar.setVisibility(View.GONE);
                            },
                            error -> {
                                viewBinding.progressBar.setVisibility(View.GONE);
                                viewBinding.registration.setVisibility(View.VISIBLE);
                                viewBinding.button.setVisibility(View.GONE);
                                viewBinding.checkBox.setVisibility(View.GONE);
                                viewBinding.imageView.setVisibility(View.GONE);
                                viewBinding.textView.setText(R.string.failed_to_import_registration);
                            }
                    ));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Iterator<Disposable> disposableIterator = disposables.iterator();
        while (disposableIterator.hasNext()) {
            Disposable disposable = disposableIterator.next();
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
            disposableIterator.remove();
        }
    }

    private void onImported() {
        Intent data = new Intent();
        if (registrationData != null) {
            data.putExtra(EXTRA_IMPORTED_FACE_COUNT, registrationData.getFaceTemplates().length);
        }
        setResult(RESULT_OK, data);
        finish();
    }

    private void onFailure(Throwable throwable) {
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
