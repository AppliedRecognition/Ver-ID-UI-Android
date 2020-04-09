package com.appliedrec.verid.ui;

import android.app.Activity;

import androidx.annotation.Nullable;

public interface ISessionActivityClassFactory {

    Class<? extends Activity> getFaceRecognitionActivityClass();

    Class<? extends Activity> getResultActivityClass(@Nullable Throwable error);
}
