package com.appliedrec.verid.sample;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;

import com.appliedrec.verid.core.FaceTemplate;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Activity that displays downloaded profile picture and registers downloaded face templates
 */
public class RegistrationImportActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.appliedrec.EXTRA_IMAGE_URI";
    public static final String EXTRA_FACE_TEMPLATES_PATH = "com.appliedred.EXTRA_FACE_TEMPLATES_PATH";

    private VerID verID;
    private ProfilePhotoHelper profilePhotoHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_import);
        int veridInstanceId = getIntent().getIntExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, -1);
        if (veridInstanceId != -1) {
            try {
                verID = VerID.getInstance(veridInstanceId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        profilePhotoHelper = new ProfilePhotoHelper(this);
        final String faceTemplatesPath;
        if (getIntent() != null) {
            Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
            faceTemplatesPath = getIntent().getStringExtra(EXTRA_FACE_TEMPLATES_PATH);
            if (imageUri != null) {
                Bitmap image = BitmapFactory.decodeFile(imageUri.getPath());
                if (image != null) {
                    ((ImageView)findViewById(R.id.imageView)).setImageBitmap(image);
                }
            }
        } else {
            faceTemplatesPath = null;
        }
        final CheckBox checkBox = findViewById(R.id.checkBox);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] users = verID.getUserManagement().getUsers();
                    if (users.length == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                checkBox.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean overwrite = checkBox.getVisibility() == View.VISIBLE && checkBox.isChecked();
                importRegistration(faceTemplatesPath, overwrite);
            }
        });
    }

    /// Begins the import of the downloaded templates
    private void importRegistration(final String faceTemplatesPath, final boolean overwrite) {
        if (overwrite) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        verID.getUserManagement().deleteUsers(new String[]{VerIDUser.DEFAULT_USER_ID});
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            amendRegistration(faceTemplatesPath);
                        }
                    });
                }
            });
        } else {
            amendRegistration(faceTemplatesPath);
        }
    }

    /// Amends registration with the supplied face templates
    private void amendRegistration(final String faceTemplatesPath) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Gson gson = new Gson();
                    File faceTemplatesFile = new File(faceTemplatesPath);
                    InputStream inputStream = new FileInputStream(faceTemplatesFile);
                    JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
                    ArrayList<FaceTemplate> templates = new ArrayList<>();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        FaceTemplate template = gson.fromJson(reader, FaceTemplate.class);
                        templates.add(template);
                    }
                    reader.endArray();
                    reader.close();
                    FaceTemplate[] faceTemplates = new FaceTemplate[templates.size()];
                    templates.toArray(faceTemplates);

                    verID.getUserManagement().assignFacesToUser(faceTemplates, VerIDUser.DEFAULT_USER_ID);
                    faceTemplatesFile.delete();
                    Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
                    if (imageUri != null) {
                        profilePhotoHelper.setProfilePhotoUri(imageUri, null);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onFailure();
                        }
                    });
                }
            }
        });
    }

    private void onFailure() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.failed_to_import_registration)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create()
                .show();
    }

    @Override
    public void finish() {
        super.finish();
        Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
        if (imageUri != null) {
            // Delete the downloaded profile picture
            new File(imageUri.getPath()).delete();
        }
    }
}
