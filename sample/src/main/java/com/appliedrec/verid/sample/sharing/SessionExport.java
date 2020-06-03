package com.appliedrec.verid.sample.sharing;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.sample.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SessionExport {

    private final VerIDSessionSettings sessionSettings;
    private final VerIDSessionResult sessionResult;
    private final EnvironmentSettings environmentSettings;

    public SessionExport(@NonNull VerIDSessionSettings sessionSettings, @NonNull VerIDSessionResult sessionResult, @NonNull EnvironmentSettings environmentSettings) {
        this.sessionSettings = sessionSettings;
        this.sessionResult = sessionResult;
        this.environmentSettings = environmentSettings;
    }

    @WorkerThread
    public Intent createShareIntent(Context context) throws Exception {
        Uri uri = createZipArchive(context);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, context.getContentResolver().getType(uri));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    @WorkerThread
    private Uri createZipArchive(Context context) throws Exception {
        File sessionsDir = new File(context.getCacheDir(), "sessions");
        //noinspection ResultOfMethodCallIgnored
        sessionsDir.mkdirs();
        File zipFile = new File(sessionsDir, "Ver-ID session "+ SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM).format(new Date()) + ".zip");
        try (FileOutputStream fileOutputStream = new FileOutputStream(zipFile)) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
                if (sessionResult.getVideoUri() != null) {
                    String videoPath = sessionResult.getVideoUri().getLastPathSegment();
                    if (videoPath != null) {
                        try (InputStream inputStream = context.getContentResolver().openInputStream(sessionResult.getVideoUri())) {
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
                        }
                    }
                }
                int i = 1;
                for (Uri imageUri : sessionResult.getImageUris()) {
                    String name = imageUri.getLastPathSegment();
                    if (name == null) {
                        continue;
                    }
                    String ext = name.substring(name.lastIndexOf(".")+1);
                    try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
                        if (inputStream != null) {
                            ZipEntry zipEntry = new ZipEntry("image" + (i++) + "." + ext);
                            zipOutputStream.putNextEntry(zipEntry);
                            byte[] buffer = new byte[512];
                            int read;
                            while ((read = inputStream.read(buffer, 0, buffer.length)) > 0) {
                                zipOutputStream.write(buffer, 0, read);
                            }
                            zipOutputStream.closeEntry();
                        }
                    }
                }
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(VerIDSessionSettings.class, new SessionSettingsDataJsonAdapter())
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
