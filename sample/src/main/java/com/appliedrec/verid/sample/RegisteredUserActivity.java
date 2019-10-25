package com.appliedrec.verid.sample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceTemplate;
import com.appliedrec.verid.core.IRecognizable;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle3.LifecycleProvider;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class RegisteredUserActivity extends AppCompatActivity {

    private static final int AUTHENTICATION_REQUEST_CODE = 0;
    private static final int REGISTRATION_REQUEST_CODE = 1;
    private static final int QR_CODE_SCAN_REQUEST_CODE = 2;

    private AlertDialog tempDialog;
    private VerID verID;
    private ProfilePhotoHelper profilePhotoHelper;
    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registered_user);
        int veridInstanceId = getIntent().getIntExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, -1);
        if (veridInstanceId != -1) {
            try {
                verID = VerID.getInstance(veridInstanceId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        profilePhotoHelper = new ProfilePhotoHelper(this);
        loadProfilePicture();
        findViewById(R.id.removeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unregisterUser();
            }
        });
        findViewById(R.id.authenticate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authenticate();
            }
        });
        findViewById(R.id.register).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerMoreFaces();
            }
        });
        // Show the registration import button if the app handles registration downloads
        findViewById(R.id.import_registration).setVisibility(((SampleApplication)getApplication()).getRegistrationDownload() != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_registration).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importRegistration();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // To inspect the result of the session:
        if (resultCode == RESULT_OK && data != null && requestCode == REGISTRATION_REQUEST_CODE) {
            VerIDSessionResult result = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (result != null) {
                Iterator<Map.Entry<Face,Uri>> faceUriIterator = result.getFaceImages(Bearing.STRAIGHT).entrySet().iterator();
                if (faceUriIterator.hasNext()) {
                    Map.Entry<Face,Uri> entry = faceUriIterator.next();
                    Rect faceRect = new Rect();
                    entry.getKey().getBounds().round(faceRect);
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(entry.getValue());
                        Bitmap fullImage = BitmapFactory.decodeStream(inputStream);
                        if (fullImage != null) {
                            Bitmap croppedImage = Bitmap.createBitmap(fullImage, faceRect.left, faceRect.top, faceRect.width(), faceRect.height());
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    profilePhotoHelper.setProfilePhotoFromUri(entry.getValue(), entry.getKey().getBounds())
                            .compose(lifecycleProvider.bindToLifecycle())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                                loadProfilePicture();
                            });
                }
            }
            // See documentation at
            // https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.VerIDSessionResult.html
        } else if (resultCode == RESULT_OK && data != null && requestCode == QR_CODE_SCAN_REQUEST_CODE && data.hasExtra(Intent.EXTRA_TEXT)) {
            tempDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.downloading)
                    .setView(new ProgressBar(this))
                    .create();
            tempDialog.show();
            RegistrationImport.importRegistration(data.getStringExtra(Intent.EXTRA_TEXT), ((SampleApplication)getApplication()).getRegistrationDownload())
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bundle -> {
                    if (tempDialog != null) {
                        tempDialog.dismiss();
                        tempDialog = null;
                    }
                    Intent intent = new Intent(RegisteredUserActivity.this, RegistrationImportActivity.class);
                    intent.putExtras(bundle);
                    intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                    startActivity(intent);
                }, error -> {
                    if (tempDialog != null) {
                        tempDialog.dismiss();
                        tempDialog = null;
                    }
                    showImportError();
                });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.registered_user, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SampleApplication app = (SampleApplication)getApplication();
        menu.findItem(R.id.action_export_registration).setVisible(app.getRegistrationUpload() != null && app.getQRCodeGenerator() != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                showIntro();
                return true;
            case R.id.action_settings:
                showSettings();
                return true;
            case R.id.action_export_registration:
                exportRegistration();
                return true;
        }
        return false;
    }

    private void loadProfilePicture() {
        final ImageView profileImageView = findViewById(R.id.profileImage);
        profileImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    profileImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    profileImageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                final int width = profileImageView.getWidth();
                profilePhotoHelper.getProfilePhotoDrawable(width)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(drawable -> {
                            profileImageView.setImageDrawable(drawable);
                        });
            }
        });
    }

    private void showIntro() {
        Intent intent = new Intent(this, IntroActivity.class);
        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
        intent.putExtra(IntroActivity.EXTRA_SHOW_REGISTRATION, false);
        startActivity(intent);
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    //region Authentication

    private void authenticate() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(new String[]{
                        "English", "Français"
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        AuthenticationSessionSettings settings = new AuthenticationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                        // This setting dictates how many poses the user will be required to move her/his head to to ensure liveness
                        // The higher the count the more confident we can be of a live face at the expense of usability
                        // Note that 1 is added to the setting to include the initial mandatory straight pose
                        settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(getString(R.string.pref_key_required_pose_count), "1")) + 1);
                        PreferenceHelper preferenceHelper = new PreferenceHelper(RegisteredUserActivity.this, preferences);
                        settings.setYawThreshold(preferenceHelper.getYawThreshold());
                        settings.setPitchThreshold(preferenceHelper.getPitchThreshold());
                        verID.getFaceRecognition().setAuthenticationThreshold(preferenceHelper.getAuthenticationThreshold());
                        // Setting showResult to false will prevent the activity from displaying a result at the end of the session
                        settings.setShowResult(true);
                        if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
                            settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                        }
                        settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 0.05f;
                        settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 0.05f;
                        Intent intent = new Intent(RegisteredUserActivity.this, VerIDSessionActivity.class);
                        intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, settings);
                        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                        if (i == 1) {
                            intent.putExtra(VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH, "fr.xml");
                        }
                        startActivityForResult(intent, AUTHENTICATION_REQUEST_CODE);
                    }
                })
                .setTitle("Select language")
                .create()
                .show();
    }
    //endregion

    //region Registration

    private void registerMoreFaces() {
        RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        // Setting showResult to false will prevent the activity from displaying a result at the end of the session
        settings.setShowResult(true);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(getString(R.string.pref_key_number_of_faces_to_register), "1")));
        settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 0.05f;
        settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 0.05f;
        if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
            settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
        }
        Intent intent = new Intent(this, VerIDSessionActivity.class);
        intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, settings);
        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
        startActivityForResult(intent, REGISTRATION_REQUEST_CODE);
    }

    private void unregisterUser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_unregister)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unregister, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Completable.create(emitter -> {
                            try {
                                verID.getUserManagement().deleteUsers(new String[]{VerIDUser.DEFAULT_USER_ID});
                                emitter.onComplete();
                            } catch (Exception e) {
                                emitter.onError(e);
                            }
                        }).compose(lifecycleProvider.bindToLifecycle()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {
                            Intent intent = new Intent(RegisteredUserActivity.this, IntroActivity.class);
                            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                            startActivity(intent);
                            finish();
                        });
                    }
                })
                .create()
                .show();
    }
    //endregion

    //region Registration import and export

    private void importRegistration() {
        // If you want to be able to import face registrations from other devices create an activity
        // that scans a QR code and returns a URL string in its intent's Intent.EXTRA_TEXT extra.
        Intent intent = new Intent("com.appliedrec.ACTION_SCAN_QR_CODE");
        startActivityForResult(intent, QR_CODE_SCAN_REQUEST_CODE);
    }

    private void exportRegistration() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.share_registration)
                .setMessage(R.string.app_will_generate_code)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.generate_code, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        uploadRegistration();
                    }
                })
                .create()
                .show();
    }

    private void uploadRegistration() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exporting_registration)
                .setView(new ProgressBar(this))
                .create();
        alertDialog.show();
        final SampleApplication app = (SampleApplication)getApplication();
        Observable<FaceTemplate> faceTemplateObservable = Observable.create(emitter -> {
            try {
                IRecognizable[] faces = verID.getUserManagement().getFacesOfUser(VerIDUser.DEFAULT_USER_ID);
                for (IRecognizable face : faces) {
                    emitter.onNext(new FaceTemplate(face.getRecognitionData(), face.getVersion(), VerIDUser.DEFAULT_USER_ID));
                }
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
        Single<Bitmap> bitmapSingle = faceTemplateObservable
                .toList()
                .flatMap(faceTemplates -> profilePhotoHelper.getProfilePhotoBitmap()
                            .flatMap(bitmap -> (SingleSource<URL>) observer -> {
                                try {
                                    RegistrationData registrationData = new RegistrationData();
                                    registrationData.setProfilePicture(bitmap);
                                    FaceTemplate[] templatesArray = new FaceTemplate[faceTemplates.size()];
                                    faceTemplates.toArray(templatesArray);
                                    registrationData.setFaceTemplates(templatesArray);
                                    URL exportURL = app.getRegistrationUpload().uploadRegistration(registrationData);
                                    observer.onSuccess(exportURL);
                                } catch (Exception e) {
                                    observer.onError(e);
                                }
                            })).flatMap(url -> observer -> {
                                try {
                                    Bitmap bitmap = app.getQRCodeGenerator().generateQRCode(url.toString());
                                    observer.onSuccess(bitmap);
                                } catch (Exception e) {
                                    observer.onError(e);
                                }
        });
        bitmapSingle.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(bitmap -> {
            alertDialog.dismiss();
            ImageView imageView = new ImageView(RegisteredUserActivity.this);
            imageView.setImageBitmap(bitmap);
            new AlertDialog.Builder(RegisteredUserActivity.this)
                    .setView(imageView)
                    .setTitle(R.string.scan_to_import)
                    .setPositiveButton(R.string.done, null)
                    .create()
                    .show();
        }, error -> {
            alertDialog.dismiss();
            showExportError();
        });
    }

    private void showExportError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.failed_to_export_registration)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    private void showImportError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.failed_to_import_registration)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    //endregion
}
