package com.appliedrec.verid.sample.sharing;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.FaceRecognition;
import com.appliedrec.verid.core2.FaceTemplate;
import com.appliedrec.verid.core2.IRecognizable;
import com.appliedrec.verid.core2.RecognizableSubject;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.VerIDFaceTemplateVersion;
import com.appliedrec.verid.proto.Registration;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.VerIDUser;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RegistrationImport {

    private final VerID verID;
    private final ProfilePhotoHelper profilePhotoHelper;

    public RegistrationImport(@NonNull VerID verID, @NonNull ProfilePhotoHelper profilePhotoHelper) {
        this.verID = verID;
        this.profilePhotoHelper = profilePhotoHelper;
    }

    public Single<Registration> registrationDataFromUri(Uri uri) {
        return Single.<Registration>create(emitter -> {
            if (!verID.getContext().isPresent()) {
                emitter.onError(new Exception("Missing context"));
                return;
            }
            try (InputStream inputStream = verID.getContext().get().getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException();
                }
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    ByteStreams.copy(inputStream, outputStream);
                    byte[] data = outputStream.toByteArray();
                    Registration registration = Registration.parseFrom(data);
                    emitter.onSuccess(registration);
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Completable importRegistration(Registration registrationData) {
        return Completable.create(emitter -> {
            try {
                byte[] image = registrationData.getImage().toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                IRecognizable[] faces = registrationData.getFacesList().stream().map(template -> new RecognizableSubject(template.getData().toByteArray(), template.getVersion())).toArray(RecognizableSubject[]::new);
                verID.getUserManagement().assignFacesToUser(faces, VerIDUser.DEFAULT_USER_ID);
                profilePhotoHelper.setProfilePhoto(bitmap);
                emitter.onComplete();
            } catch (VerIDCoreException | IOException e) {
                emitter.onError(e);
            }
        });
    }
}
