package com.appliedrec.verid.sample.sharing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.VerIDUser;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class RegistrationImport {

    private final VerID verID;
    private final ProfilePhotoHelper profilePhotoHelper;

    public RegistrationImport(@NonNull VerID verID, @NonNull ProfilePhotoHelper profilePhotoHelper) {
        this.verID = verID;
        this.profilePhotoHelper = profilePhotoHelper;
    }

    @WorkerThread
    public void importRegistration(Context context, Uri uri) throws Exception {
        RegistrationData registrationData = registrationDataFromUri(context, uri);
        importRegistration(registrationData);
    }

    @WorkerThread
    public RegistrationData registrationDataFromUri(Context context, Uri uri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException();
            }
            try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                Gson gson = new Gson();
                try (JsonReader jsonReader = gson.newJsonReader(inputStreamReader)) {
                    return gson.fromJson(jsonReader, RegistrationData.class);
                }
            }
        }
    }

    @WorkerThread
    public void importRegistration(RegistrationData registrationData) throws Exception {
        verID.getUserManagement().assignFacesToUser(registrationData.getFaceTemplates(), VerIDUser.DEFAULT_USER_ID);
        profilePhotoHelper.setProfilePhoto(registrationData.getProfilePicture());
    }
}
