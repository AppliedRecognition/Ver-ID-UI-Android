package com.appliedrec.verid.sample;

import android.graphics.Bitmap;

import com.appliedrec.verid.core.FaceTemplate;
import com.appliedrec.verid.core.RecognizableSubject;

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
