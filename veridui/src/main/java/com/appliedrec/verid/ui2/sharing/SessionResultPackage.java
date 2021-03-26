package com.appliedrec.verid.ui2.sharing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Keep;

import com.appliedrec.verid.core2.FaceDetection;
import com.appliedrec.verid.core2.IFaceDetection;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Session Result Package
 * @since 2.0.0
 */
@Keep
public class SessionResultPackage {

    private final Context context;
    private VerIDSessionSettings settings;
    private VerIDSessionResult result;
    private EnvironmentSettings environmentSettings;

    /**
     * Constructor
     * @param verID VerID instance that ran the session
     * @param settings Settings used by the session
     * @param result Session result
     * @since 2.0.0
     */
    @Keep
    public SessionResultPackage(VerID verID, VerIDSessionSettings settings, VerIDSessionResult result) throws Exception {
        this.context = verID.getContext().orElseThrow(Exception::new).getApplicationContext();
        this.settings = settings;
        this.result = result;
        IFaceDetection<?> faceDetection = verID.getFaceDetection();
        this.environmentSettings = new EnvironmentSettings(
                ((FaceDetection) faceDetection).detRecLib.getSettings().getConfidenceThreshold(),
                ((FaceDetection) faceDetection).getFaceExtractQualityThreshold(),
                ((FaceDetection) faceDetection).getLandmarkTrackingQualityThreshold(),
                VerID.getVersion()
        );
    }

    @Keep
    public VerIDSessionSettings getSettings() {
        return settings;
    }

    @Keep
    public VerIDSessionResult getResult() {
        return result;
    }

    @Keep
    public EnvironmentSettings getEnvironmentSettings() {
        return environmentSettings;
    }

    /**
     * Archive the package to an output stream
     * @param outputStream Stream into which to write the package
     * @throws IOException If the write fails
     * @since 2.0.0
     */
    @Keep
    public void archiveToStream(OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            result.getVideoUri().ifPresent(videoUri -> {
                String videoPath = videoUri.getLastPathSegment();
                if (videoPath != null) {
                    try (InputStream inputStream = context.getContentResolver().openInputStream(videoUri)) {
                        if (inputStream != null) {
                            ZipEntry zipEntry = new ZipEntry("video" + videoPath.substring(videoPath.lastIndexOf(".")));
                            zipOutputStream.putNextEntry(zipEntry);
                            byte[] buffer = new byte[512];
                            int read;
                            while ((read = inputStream.read(buffer, 0, buffer.length)) > 0) {
                                zipOutputStream.write(buffer, 0, read);
                            }
                            zipOutputStream.closeEntry();
                        }
                    } catch (Exception ignore) {
                    }
                }
            });
            int i = 1;
            for (FaceCapture faceCapture : result.getFaceCaptures()) {
                ZipEntry zipEntry = new ZipEntry("image" + (i++) + ".jpg");
                zipOutputStream.putNextEntry(zipEntry);
                faceCapture.getImage().compress(Bitmap.CompressFormat.JPEG, 80, zipOutputStream);
                zipOutputStream.closeEntry();
            }
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(VerIDSessionResult.class, new SessionResultDataJsonAdapter())
                    .registerTypeAdapter(VerIDSessionSettings.class, new SessionSettingsDataJsonAdapter())
                    .registerTypeAdapter(EnvironmentSettings.class, new EnvironmentSettingsJsonAdapter())
                    .create();
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(zipOutputStream)) {
                try (JsonWriter jsonWriter = gson.newJsonWriter(outputStreamWriter)) {
                    ZipEntry zipEntry = new ZipEntry("settings.json");
                    zipOutputStream.putNextEntry(zipEntry);
                    gson.toJson(settings, VerIDSessionSettings.class, jsonWriter);
                    jsonWriter.flush();
                    zipOutputStream.closeEntry();
                    zipEntry = new ZipEntry("result.json");
                    zipOutputStream.putNextEntry(zipEntry);
                    gson.toJson(result, VerIDSessionResult.class, jsonWriter);
                    jsonWriter.flush();
                    zipOutputStream.closeEntry();
                    zipEntry = new ZipEntry("environment.json");
                    zipOutputStream.putNextEntry(zipEntry);
                    gson.toJson(environmentSettings, EnvironmentSettings.class, jsonWriter);
                    jsonWriter.flush();
                    zipOutputStream.closeEntry();
                    zipOutputStream.finish();
                }
            }
        }
    }

    @Keep
    public SessionResultPackage(Context context, InputStream inputStream) throws Exception {
        this.context = context.getApplicationContext();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(EnvironmentSettings.class, new EnvironmentSettingsJsonAdapter())
                    .registerTypeAdapter(VerIDSessionResult.class, new SessionResultDataJsonAdapter())
                    .registerTypeAdapter(VerIDSessionSettings.class, new SessionSettingsDataJsonAdapter())
                    .create();
            try (JsonReader jsonReader = gson.newJsonReader(new InputStreamReader(zipInputStream))) {
                ArrayList<Bitmap> images = new ArrayList<>();
                Uri videoUri = null;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    switch (zipEntry.getName()) {
                        case "settings.json":
                            settings = gson.fromJson(jsonReader, VerIDSessionSettings.class);
                            break;
                        case "result.json":
                            result = gson.fromJson(jsonReader, VerIDSessionResult.class);
                            break;
                        case "environment.json":
                            environmentSettings = gson.fromJson(jsonReader, EnvironmentSettings.class);
                            break;
                        default:
                            if (zipEntry.getName().startsWith("image") && zipEntry.getName().endsWith(".jpg")) {
                                Bitmap image = BitmapFactory.decodeStream(zipInputStream);
                                if (image != null) {
                                    images.add(image);
                                }
                            } else if (zipEntry.getName().startsWith("video")) {
                                File videoFile = File.createTempFile("video", zipEntry.getName().substring(zipEntry.getName().lastIndexOf(".")-1));
                                try (FileOutputStream fileOutputStream = new FileOutputStream(videoFile)) {
                                    int read;
                                    byte[] buffer = new byte[512];
                                    while ((read = zipInputStream.read(buffer, 0, buffer.length)) > 0) {
                                        fileOutputStream.write(buffer, 0, read);
                                    }
                                    videoUri = Uri.fromFile(videoFile);
                                }
                            }
                    }
                }
                if (result != null && result.getFaceCaptures().length == images.size()) {
                    ArrayList<FaceCapture> faceCaptures = new ArrayList<>();
                    int i = 0;
                    for (FaceCapture faceCapture : result.getFaceCaptures()) {
                        FaceCapture fc = new FaceCapture(faceCapture.getFace(), faceCapture.getBearing(), images.get(i++));
                        faceCaptures.add(fc);
                    }
                    VerIDSessionException exception = result.getError().orElse(null);
                    result = new VerIDSessionResult(faceCaptures, result.getSessionStartTime().getTime(), result.getSessionStartTime().getTime()+result.getSessionDuration(TimeUnit.MILLISECONDS), result.getSessionDiagnostics().orElse(null));
                    result.setError(exception);
                }
                if (result != null && videoUri != null) {
                    result.setVideoUri(videoUri);
                }
            }
        }
    }
}
