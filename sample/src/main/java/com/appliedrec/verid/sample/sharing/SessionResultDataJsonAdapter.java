package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.FaceJsonAdapter;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.serialization.Cbor;
import com.appliedrec.verid.core2.serialization.CborCoder;
import com.appliedrec.verid.core2.serialization.CborEncoder;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.SessionDiagnostics;
import com.appliedrec.verid.core2.session.SessionDiagnosticsJsonAdapter;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

class SessionResultDataJsonAdapter implements JsonSerializer<VerIDSessionResult>, CborEncoder<VerIDSessionResult> {

    @Override
    public JsonElement serialize(VerIDSessionResult src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonArray faces = new JsonArray();
        for (FaceCapture attachment : src.getFaceCaptures()) {
            JsonObject face = new JsonObject();
            face.add("face", context.serialize(attachment.getFace(), RecognizableFace.class));
            face.add("bearing", context.serialize(attachment.getBearing(), Bearing.class));
            faces.add(face);
        }
        jsonObject.add("face_captures", faces);
        src.getSessionDiagnostics().ifPresent(sessionDiagnostics -> {
            jsonObject.add("diagnostics", context.serialize(sessionDiagnostics, SessionDiagnostics.class));
        });
        jsonObject.addProperty("start_time", src.getSessionStartTime().getTime());
        jsonObject.addProperty("duration_seconds", src.getSessionDuration(TimeUnit.SECONDS));
        if (!src.getError().isPresent()) {
            jsonObject.addProperty("succeeded", true);
        } else {
            jsonObject.addProperty("error", src.getError().get().toString());
            jsonObject.addProperty("succeeded", false);
        }
        return jsonObject;
    }

    @Override
    public void encodeToCbor(VerIDSessionResult src, com.fasterxml.jackson.dataformat.cbor.CBORGenerator cborGenerator) throws Exception {
        cborGenerator.writeStartObject();
        if (src.getFaceCaptures().length > 0) {
            cborGenerator.writeArrayFieldStart("face_captures");
            for (FaceCapture faceCapture : src.getFaceCaptures()) {
                cborGenerator.writeStartObject();
                cborGenerator.writeFieldName("face");
                new FaceJsonAdapter().encodeToCbor(faceCapture.getFace(), cborGenerator);
                cborGenerator.writeStringField("bearing", faceCapture.getBearing().name());
                cborGenerator.writeEndObject();
            }
            cborGenerator.writeEndArray();
        }
        src.getSessionDiagnostics().ifPresent(sessionDiagnostics -> {
            try {
                cborGenerator.writeFieldName("diagnostics");
                new SessionDiagnosticsJsonAdapter().encodeToCbor(sessionDiagnostics, cborGenerator);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        cborGenerator.writeNumberField("start_time", src.getSessionStartTime().getTime());
        cborGenerator.writeNumberField("duration_seconds", src.getSessionDuration(TimeUnit.SECONDS));
        if (!src.getError().isPresent()) {
            cborGenerator.writeBooleanField("succeeded", true);
        } else {
            cborGenerator.writeBooleanField("succeeded", false);
            cborGenerator.writeStringField("error", src.getError().get().toString());
        }
        cborGenerator.writeEndObject();
    }
}
