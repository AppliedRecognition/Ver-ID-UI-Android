package com.appliedrec.verid.sample;

import androidx.annotation.WorkerThread;

import java.net.URL;

public interface IRegistrationUpload {

    /**
     * Uploads registration data to a backend for sharing
     * @param registrationData Registration data (face templates and profile picture) to upload
     * @return URL where the registration data can be downloaded
     * @throws Exception
     */
    @WorkerThread
    URL uploadRegistration(RegistrationData registrationData) throws Exception;
}
