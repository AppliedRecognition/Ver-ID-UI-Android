package com.appliedrec.verid.sample.sharing;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.appliedrec.verid.core2.IRecognizable;
import com.appliedrec.verid.core2.RecognizableSubject;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.sample.BuildConfig;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.VerIDUser;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RegistrationExport {

    private final VerID verID;
    private final ProfilePhotoHelper profilePhotoHelper;

    public RegistrationExport(@NonNull VerID verid, @NonNull ProfilePhotoHelper profilePhotoHelper) {
        this.verID = verid;
        this.profilePhotoHelper = profilePhotoHelper;
    }

    @WorkerThread
    private RegistrationData createRegistrationData() throws Exception {
        RegistrationData registrationData = new RegistrationData();
        IRecognizable[] faces = verID.getUserManagement().getFacesOfUser(VerIDUser.DEFAULT_USER_ID);
        RecognizableSubject[] subjects = new RecognizableSubject[faces.length];
        int i = 0;
        for (IRecognizable face : faces) {
            RecognizableSubject subject = new RecognizableSubject(face.getRecognitionData(), face.getVersion());
            subjects[i++] = subject;
        }
        Bitmap photo = profilePhotoHelper.getProfilePhoto();
        registrationData.setFaceTemplates(subjects);
        registrationData.setProfilePicture(photo);
        return registrationData;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @WorkerThread
    public Intent createShareIntent(Context context) throws Exception {
        RegistrationData registrationData = createRegistrationData();
        File file = new File(context.getCacheDir(), "registrations");
        file.mkdirs();
        File registrationFile = new File(file, "Registration.verid");
        if (registrationFile.exists()) {
            registrationFile.delete();
        }
        try (FileWriter fileWriter = new FileWriter(registrationFile)) {
            Gson gson = new Gson();
            try (JsonWriter jsonWriter = gson.newJsonWriter(fileWriter)) {
                gson.toJson(registrationData, RegistrationData.class, jsonWriter);
                jsonWriter.flush();
                Uri registrationUri = SampleAppFileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", registrationFile);
                if (registrationUri == null) {
                    throw new IOException();
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(registrationUri, "application/verid-registration");
                intent.putExtra(Intent.EXTRA_STREAM, registrationUri);
                return intent;
            }
        }
    }
}
