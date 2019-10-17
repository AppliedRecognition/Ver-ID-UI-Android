package com.appliedrec.verid.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.loader.content.AsyncTaskLoader;

import com.appliedrec.verid.core.FaceTemplate;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;

/**
 * Downloads registration data (face templates and profile picture) from a URL using the supplied IRegistrationDownload implementation
 */
public class RegistrationImportLoader extends AsyncTaskLoader<Bundle> {

    private String url;
    private IRegistrationDownload registrationDownload;

    public RegistrationImportLoader(Context context, String url, IRegistrationDownload registrationDownload) {
        super(context);
        this.registrationDownload = registrationDownload;
        this.url = url;
    }
    @Override
    public Bundle loadInBackground() {
        try {
            final URL url = new URL(this.url);
            RegistrationData registrationData = registrationDownload.downloadRegistration(url);
            FaceTemplate[] faceTemplates = registrationData.getFaceTemplates();
            Bitmap profileImage = registrationData.getProfilePicture();
            if (faceTemplates == null || faceTemplates.length == 0) {
                return null;
            }
            if (profileImage == null) {
                return null;
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
            return bundle;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
