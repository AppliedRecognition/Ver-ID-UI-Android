package com.appliedrec.verid.sample;

import androidx.annotation.WorkerThread;

import java.net.URL;

public interface IRegistrationDownload {

    /**
     * Downloads registration data from the given URL
     * @param source URL from where to download the registration data
     * @return Registration data (face templates and profile picture)
     * @throws Exception
     */
    @WorkerThread
    RegistrationData downloadRegistration(URL source) throws Exception;
}