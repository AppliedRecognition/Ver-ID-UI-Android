package com.appliedrec.verid.ui2.sharing;

import android.graphics.Bitmap;
import android.media.ExifInterface;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.VerIDImageBitmap;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.SessionDiagnostics;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SessionResultDataJsonAdapter implements JsonSerializer<VerIDSessionResult>, JsonDeserializer<VerIDSessionResult> {

    @Override
    public JsonElement serialize(VerIDSessionResult src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonArray faces = new JsonArray();
        for (FaceCapture attachment : src.getFaceCaptures()) {
            JsonObject face = new JsonObject();
            face.add("face", context.serialize(attachment.getFace(), RecognizableFace.class));
            face.add("bearing", context.serialize(attachment.getBearing(), Bearing.class));
            if (!attachment.getDiagnosticInfo().isEmpty()) {
                face.add("diagnostic_info", context.serialize(attachment.getDiagnosticInfo()));
            }
            faces.add(face);
        }
        jsonObject.add("face_captures", faces);
        src.getSessionDiagnostics().ifPresent(sessionDiagnostics -> {
            jsonObject.add("diagnostics", context.serialize(sessionDiagnostics, SessionDiagnostics.class));
        });
        jsonObject.addProperty("start_time", src.getSessionStartTime().getTime()/1000);
        jsonObject.addProperty("duration_seconds", src.getSessionDuration(TimeUnit.SECONDS));
        if (src.getError().isEmpty()) {
            jsonObject.addProperty("succeeded", true);
        } else {
            jsonObject.addProperty("error", src.getError().get().getCode().name());
            jsonObject.addProperty("succeeded", false);
        }
        return jsonObject;
    }

    @Override
    public VerIDSessionResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        long startTime = jsonObject.get("start_time").getAsLong()*1000;
        long duration = jsonObject.get("duration_seconds").getAsLong();
        long endTime = startTime + duration * 1000;
        SessionDiagnostics diagnostics;
        if (jsonObject.has("diagnostics")) {
            diagnostics = context.deserialize(jsonObject.get("diagnostics"), SessionDiagnostics.class);
        } else {
            diagnostics = null;
        }
        VerIDSessionResult sessionResult;
        VerIDSessionException sessionException;
        if (!jsonObject.get("succeeded").getAsBoolean()) {
            sessionException = new VerIDSessionException(VerIDSessionException.Code.valueOf(jsonObject.get("error").getAsString()));
        } else {
            sessionException = null;
        }
        sessionResult = new VerIDSessionResult(sessionException, startTime, endTime, diagnostics);
        if (jsonObject.has("face_captures")) {
            ArrayList<FaceCapture> faceCaptures = new ArrayList<>();
            JsonArray jsonFaceCaptures = jsonObject.getAsJsonArray("face_captures");
            for (JsonElement element : jsonFaceCaptures) {
                JsonObject faceCapture = element.getAsJsonObject();
                RecognizableFace face = context.deserialize(faceCapture.get("face"), RecognizableFace.class);
                Bearing bearing = context.deserialize(faceCapture.get("bearing"), Bearing.class);
                Bitmap bitmap = Bitmap.createBitmap((int)Math.ceil(face.getBounds().right), (int)Math.ceil(face.getBounds().bottom), Bitmap.Config.ARGB_8888);
                VerIDImageBitmap image = new VerIDImageBitmap(bitmap, ExifInterface.ORIENTATION_NORMAL);
                FaceCapture capture = new FaceCapture(face, bearing, bitmap, image.provideVerIDImage());
                if (faceCapture.has("diagnostic_info")) {
                    FaceCapture.DiagnosticInfo diagnosticInfo = context.deserialize(faceCapture.get("diagnostic_info"), FaceCapture.DiagnosticInfo.class);
                    capture.setDiagnosticInfo(diagnosticInfo);
                }
                faceCaptures.add(capture);
            }
            sessionResult = new VerIDSessionResult(faceCaptures, startTime, endTime, diagnostics);
            if (sessionException != null) {
                sessionResult.setError(sessionException);
            }
        }
        return sessionResult;
    }
}
