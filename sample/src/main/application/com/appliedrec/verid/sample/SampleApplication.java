package com.appliedrec.verid.sample;

import android.app.Application;

public class SampleApplication extends Application {

    public IRegistrationUpload getRegistrationUpload() {
        return null;
    }

    public IRegistrationDownload getRegistrationDownload() {
        return null;
    }

    public IQRCodeGenerator getQRCodeGenerator() {
        return null;
    }
}
