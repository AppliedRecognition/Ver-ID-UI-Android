package com.appliedrec.verid.sample.sharing;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettingsJsonAdapter;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettingsJsonAdapter;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettingsJsonAdapter;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionResultJsonAdapter;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.sample.BuildConfig;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.reactivex.rxjava3.core.Single;

public class SessionExport {

    private final VerIDSessionSettings sessionSettings;
    private final VerIDSessionResult sessionResult;
    private final EnvironmentSettings environmentSettings;

    public SessionExport(@NonNull VerIDSessionSettings sessionSettings, @NonNull VerIDSessionResult sessionResult, @NonNull EnvironmentSettings environmentSettings) {
        this.sessionSettings = sessionSettings;
        this.sessionResult = sessionResult;
        this.environmentSettings = environmentSettings;
    }

    public Single<Intent> createShareIntent(Context context) {
        return Single.create(emitter -> {
            try {
                Uri uri = createZipArchive(context);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uri, context.getContentResolver().getType(uri));
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                emitter.onSuccess(intent);
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    @WorkerThread
    private Uri createCborFile(Context context) throws Exception {
        File sessionsDir = new File(context.getCacheDir(), "sessions");
        //noinspection ResultOfMethodCallIgnored
        sessionsDir.mkdirs();
        File cborFile = new File(sessionsDir, "Ver-ID session "+ SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM).format(new Date()) + ".cbor");
        try (FileOutputStream fileOutputStream = new FileOutputStream(cborFile)) {
            CBORGenerator cborGenerator = new CBORFactory().createGenerator(fileOutputStream);
            cborGenerator.writeStartObject();
            cborGenerator.writeFieldName("settings");
            if (sessionSettings instanceof AuthenticationSessionSettings) {
                new AuthenticationSessionSettingsJsonAdapter().encodeToCbor((AuthenticationSessionSettings)sessionSettings, cborGenerator);
            } else if (sessionSettings instanceof RegistrationSessionSettings) {
                new RegistrationSessionSettingsJsonAdapter().encodeToCbor((RegistrationSessionSettings)sessionSettings, cborGenerator);
            } else if (sessionSettings instanceof LivenessDetectionSessionSettings) {
                new LivenessDetectionSessionSettingsJsonAdapter().encodeToCbor((LivenessDetectionSessionSettings)sessionSettings, cborGenerator);
            } else {
                throw new Exception("Unsupported session settings type");
            }
            cborGenerator.writeFieldName("result");
            new VerIDSessionResultJsonAdapter().encodeToCbor(sessionResult, cborGenerator);
            cborGenerator.writeFieldName("environment");
            new EnvironmentSettingsJsonAdapter().encodeToCbor(environmentSettings, cborGenerator);
            cborGenerator.writeEndObject();
            cborGenerator.flush();
            return SampleAppFileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", cborFile);
        }
    }

    private String getFileName() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.CANADA);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return "Ver-ID session "+ dateFormat.format(sessionResult.getSessionStartTime()) + ".zip";
    }

    @WorkerThread
    private Uri createZipArchive(Context context) throws Exception {
        File sessionsDir = new File(context.getCacheDir(), "sessions");
        //noinspection ResultOfMethodCallIgnored
        sessionsDir.mkdirs();
        File zipFile = new File(sessionsDir, getFileName());
        try (FileOutputStream fileOutputStream = new FileOutputStream(zipFile)) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
                sessionResult.getVideoUri().ifPresent(videoUri -> {
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
                for (FaceCapture faceCapture : sessionResult.getFaceCaptures()) {
                    ZipEntry zipEntry = new ZipEntry("image" + (i++) + ".jpg");
                    zipOutputStream.putNextEntry(zipEntry);
                    faceCapture.getImage().compress(Bitmap.CompressFormat.JPEG, 80, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(VerIDSessionResult.class, new SessionResultDataJsonAdapter())
                        .create();
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(zipOutputStream)) {
                    try (JsonWriter jsonWriter = gson.newJsonWriter(outputStreamWriter)) {
                        ZipEntry zipEntry = new ZipEntry("settings.json");
                        zipOutputStream.putNextEntry(zipEntry);
                        gson.toJson(sessionSettings, VerIDSessionSettings.class, jsonWriter);
                        jsonWriter.flush();
                        zipOutputStream.closeEntry();
                        zipEntry = new ZipEntry("result.json");
                        zipOutputStream.putNextEntry(zipEntry);
                        gson.toJson(sessionResult, VerIDSessionResult.class, jsonWriter);
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
        return SampleAppFileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".fileprovider", zipFile);
    }
}
