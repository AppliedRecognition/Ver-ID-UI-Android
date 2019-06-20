package com.appliedrec.verid.sample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
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
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

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

import java.net.URL;
import java.util.Iterator;
import java.util.Map;

public class RegisteredUserActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks {

    private static final int AUTHENTICATION_REQUEST_CODE = 0;
    private static final int REGISTRATION_REQUEST_CODE = 1;
    private static final int QR_CODE_SCAN_REQUEST_CODE = 2;
    private static final int LOADER_ID_REGISTRATION_IMPORT = 0;

    private AlertDialog tempDialog;
    private VerID verID;
    private ProfilePhotoHelper profilePhotoHelper;

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
                    profilePhotoHelper.setProfilePhotoUri(entry.getValue(), entry.getKey().getBounds());
                    loadProfilePicture();
                }
            }
            // See documentation at
            // https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.VerIDSessionResult.html
        } else if (resultCode == RESULT_OK && data != null && requestCode == QR_CODE_SCAN_REQUEST_CODE && data.hasExtra(Intent.EXTRA_TEXT)) {
            LoaderManager.getInstance(this).restartLoader(LOADER_ID_REGISTRATION_IMPORT, data.getExtras(), this).forceLoad();
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
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Uri profilePhotoUri = profilePhotoHelper.getProfilePhotoUri();
                        Bitmap grayscaleBitmap = BitmapFactory.decodeFile(profilePhotoUri.getPath());
                        if (grayscaleBitmap != null) {
                            int size = Math.min(grayscaleBitmap.getWidth(), grayscaleBitmap.getHeight());
                            int x = (int) ((double) grayscaleBitmap.getWidth() / 2.0 - (double) size / 2.0);
                            int y = (int) ((double) grayscaleBitmap.getHeight() / 2.0 - (double) size / 2.0);
                            grayscaleBitmap = Bitmap.createBitmap(grayscaleBitmap, x, y, size, size);
                            grayscaleBitmap = Bitmap.createScaledBitmap(grayscaleBitmap, width, width, true);
                            final RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), grayscaleBitmap);
                            roundedBitmapDrawable.setCornerRadius((float) width / 2f);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    profileImageView.setImageDrawable(roundedBitmapDrawable);
                                }
                            });
                        }
                    }
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
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    verID.getUserManagement().deleteUsers(new String[]{VerIDUser.DEFAULT_USER_ID});
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Intent intent = new Intent(RegisteredUserActivity.this, IntroActivity.class);
                                            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                                            startActivity(intent);
                                            finish();
                                        }
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
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
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SampleApplication app = (SampleApplication)getApplication();
                    IRecognizable[] faces = verID.getUserManagement().getFacesOfUser(VerIDUser.DEFAULT_USER_ID);
                    FaceTemplate[] faceTemplates = new FaceTemplate[faces.length];
                    for (int i=0; i<faces.length; i++) {
                        faceTemplates[i] = new FaceTemplate(faces[i].getRecognitionData(), faces[i].getVersion(), VerIDUser.DEFAULT_USER_ID);
                    }
                    Bitmap profilePicture = BitmapFactory.decodeFile(profilePhotoHelper.getProfilePhotoUri().getPath());
                    RegistrationData registrationData = new RegistrationData();
                    registrationData.setProfilePicture(profilePicture);
                    registrationData.setFaceTemplates(faceTemplates);
                    URL exportURL = app.getRegistrationUpload().uploadRegistration(registrationData);
                    final Bitmap bitmap = app.getQRCodeGenerator().generateQRCode(exportURL.toString());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            alertDialog.dismiss();
                            ImageView imageView = new ImageView(RegisteredUserActivity.this);
                            imageView.setImageBitmap(bitmap);
                            new AlertDialog.Builder(RegisteredUserActivity.this)
                                    .setView(imageView)
                                    .setTitle(R.string.scan_to_import)
                                    .setPositiveButton(R.string.done, null)
                                    .create()
                                    .show();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            alertDialog.dismiss();
                            showExportError();
                        }
                    });
                }
            }
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

    @Override
    public Loader onCreateLoader(int id, Bundle args) {
        if (id == LOADER_ID_REGISTRATION_IMPORT) {
            tempDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.downloading)
                    .setView(new ProgressBar(this))
                    .create();
            tempDialog.show();
            String url = args.getString(Intent.EXTRA_TEXT);
            return new RegistrationImportLoader(this, url, ((SampleApplication)getApplication()).getRegistrationDownload());
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader loader, Object data) {
        if (loader.getId() == LOADER_ID_REGISTRATION_IMPORT) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tempDialog != null) {
                        tempDialog.dismiss();
                        tempDialog = null;
                    }
                }
            });
            if (data != null && data instanceof Bundle) {
                final Bundle extras = (Bundle) data;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(RegisteredUserActivity.this, RegistrationImportActivity.class);
                        intent.putExtras(extras);
                        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                        startActivity(intent);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showImportError();
                    }
                });
            }
        }
    }

    @Override
    public void onLoaderReset(Loader loader) {

    }
    //endregion
}
