package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

class SessionResultDataJsonAdapter implements JsonSerializer<VerIDSessionResult> {

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
        jsonObject.add("faces", faces);
        jsonObject.addProperty("start_time", src.getSessionStartTime().getTime());
        jsonObject.addProperty("duration_seconds", src.getSessionDuration(TimeUnit.SECONDS));
        if (!src.getError().isPresent()) {
            jsonObject.addProperty("succeeded", true);
        } else {
            jsonObject.addProperty("error", src.getError().toString());
            jsonObject.addProperty("succeeded", false);
        }
        return jsonObject;
    }
}
