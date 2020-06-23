package com.appliedrec.verid.sample.sharing;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.VerIDUser;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

class RegistrationImport {

    private final VerID verID;
    private final ProfilePhotoHelper profilePhotoHelper;

    public RegistrationImport(@NonNull VerID verID, @NonNull ProfilePhotoHelper profilePhotoHelper) {
        this.verID = verID;
        this.profilePhotoHelper = profilePhotoHelper;
    }

    public Single<RegistrationData> registrationDataFromUri(Uri uri) {
        return Single.<RegistrationData>create(emitter -> {
            if (!verID.getContext().isPresent()) {
                emitter.onError(new Exception("Missing context"));
                return;
            }
            try (InputStream inputStream = verID.getContext().get().getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException();
                }
                try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                    Gson gson = new Gson();
                    try (JsonReader jsonReader = gson.newJsonReader(inputStreamReader)) {
                        emitter.onSuccess(gson.fromJson(jsonReader, RegistrationData.class));
                    }
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Completable importRegistration(RegistrationData registrationData) {
        return Completable.create(emitter -> {
            try {
                verID.getUserManagement().assignFacesToUser(registrationData.getFaceTemplates(), VerIDUser.DEFAULT_USER_ID);
                profilePhotoHelper.setProfilePhoto(registrationData.getProfilePicture());
                emitter.onComplete();
            } catch (VerIDCoreException | IOException e) {
                emitter.onError(e);
            }
        });
    }
}
