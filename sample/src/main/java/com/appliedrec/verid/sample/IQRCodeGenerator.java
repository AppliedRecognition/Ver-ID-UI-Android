package com.appliedrec.verid.sample;

import android.graphics.Bitmap;

public interface IQRCodeGenerator {

    Bitmap generateQRCode(String payload) throws Exception;
}
