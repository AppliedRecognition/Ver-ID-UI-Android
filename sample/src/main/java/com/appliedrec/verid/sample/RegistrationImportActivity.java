package com.appliedrec.verid.sample;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core.FaceTemplate;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.reactivex.Completable;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Activity that displays downloaded profile picture and registers downloaded face templates
 */
public class RegistrationImportActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.appliedrec.EXTRA_IMAGE_URI";
    public static final String EXTRA_FACE_TEMPLATES_PATH = "com.appliedred.EXTRA_FACE_TEMPLATES_PATH";

    private ProfilePhotoHelper profilePhotoHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_import);
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
        checkBox.setVisibility(View.GONE);
        RxVerIDInstance.get()
                .getUsers()
                .firstElement()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> checkBox.setVisibility(View.VISIBLE),
                        error -> {},
                        () -> checkBox.setVisibility(View.GONE)
                );
        findViewById(R.id.button).setOnClickListener(v -> {
                boolean overwrite = checkBox.getVisibility() == View.VISIBLE && checkBox.isChecked();
                importRegistration(faceTemplatesPath, overwrite);
            });
    }

    /// Begins the import of the downloaded templates
    private void importRegistration(final String faceTemplatesPath, final boolean overwrite) {
        Completable completable;
        if (overwrite) {
            completable = RxVerIDInstance.get().deleteUser(VerIDUser.DEFAULT_USER_ID);
        } else {
            completable = Completable.complete();
        }
        completable
                .andThen(amendRegistration(faceTemplatesPath))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    finish();
                }, error -> {
                    onFailure();
                });
    }

    /// Amends registration with the supplied face templates
    private Completable amendRegistration(String faceTemplatesPath) {
        Observable<FaceTemplate> templateObservable = Observable.create(emitter -> {
            try {
                Gson gson = new Gson();
                File faceTemplatesFile = new File(faceTemplatesPath);
                InputStream inputStream = new FileInputStream(faceTemplatesFile);
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
                reader.beginArray();
                while (reader.hasNext()) {
                    FaceTemplate template = gson.fromJson(reader, FaceTemplate.class);
                    emitter.onNext(template);
                }
                reader.endArray();
                reader.close();
                faceTemplatesFile.delete();
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
        return templateObservable
                .flatMapCompletable(faceTemplate -> RxVerIDInstance.get().assignFaceToUser(faceTemplate, VerIDUser.DEFAULT_USER_ID))
                .andThen((MaybeSource<Uri>) emitter -> {
                    Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
                    if (imageUri != null) {
                        emitter.onSuccess(imageUri);
                    } else {
                        emitter.onComplete();
                    }
                }).flatMapCompletable(imageUri -> profilePhotoHelper.setProfilePhotoFromUri(imageUri, null));
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
