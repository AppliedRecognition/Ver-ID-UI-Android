package com.appliedrec.verid.sample;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import com.appliedrec.verid.core.FaceTemplate;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;

import io.reactivex.Single;

public class RegistrationImport {

    public static Single<Bundle> importRegistration(String urlString, IRegistrationDownload registrationDownload) {
        return Single.create(emitter -> {
            try {
                final URL url = new URL(urlString);
                RegistrationData registrationData = registrationDownload.downloadRegistration(url);
                FaceTemplate[] faceTemplates = registrationData.getFaceTemplates();
                Bitmap profileImage = registrationData.getProfilePicture();
                if (faceTemplates == null || faceTemplates.length == 0) {
                    throw new Exception("No templates to import");
                }
                if (profileImage == null) {
                    throw new Exception("Missing profile picture");
                }
                final File faceTemplatesFile = File.createTempFile("faces_",".json");
                OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(faceTemplatesFile));
                Gson gson = new Gson();
                writer.write(gson.toJson(faceTemplates));
                writer.close();
                final File imageFile = File.createTempFile("profile_", ".jpg");
                OutputStream outputStream = new FileOutputStream(imageFile);
                profileImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                Bundle bundle = new Bundle();
                bundle.putParcelable(RegistrationImportActivity.EXTRA_IMAGE_URI, Uri.fromFile(imageFile));
                bundle.putString(RegistrationImportActivity.EXTRA_FACE_TEMPLATES_PATH, faceTemplatesFile.getPath());
                emitter.onSuccess(bundle);
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }
}
