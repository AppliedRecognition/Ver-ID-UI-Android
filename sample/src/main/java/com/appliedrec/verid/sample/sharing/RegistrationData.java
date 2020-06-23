package com.appliedrec.verid.sample.sharing;

import android.graphics.Bitmap;

import com.appliedrec.verid.core2.RecognizableSubject;
import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(RegistrationDataJsonAdapter.class)
public class RegistrationData {

    private RecognizableSubject[] faceTemplates;
    private Bitmap profilePicture;

    public RecognizableSubject[] getFaceTemplates() {
        return faceTemplates;
    }

    public Bitmap getProfilePicture() {
        return profilePicture;
    }

    public void setFaceTemplates(RecognizableSubject[] faceTemplates) {
        this.faceTemplates = faceTemplates;
    }

    public void setProfilePicture(Bitmap profilePicture) {
        this.profilePicture = profilePicture;
    }
}
