package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.DetectedFace;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.RecognizableFace;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

class SessionResultDataJsonAdapter implements JsonSerializer<VerIDSessionResult> {

    @Override
    public JsonElement serialize(VerIDSessionResult src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonArray faces = new JsonArray();
        for (DetectedFace attachment : src.getAttachments()) {
            JsonObject face = new JsonObject();
            Class<?> type = attachment.getFace() instanceof RecognizableFace ? RecognizableFace.class : Face.class;
            face.add("face", context.serialize(attachment.getFace(), type));
            face.add("bearing", context.serialize(attachment.getBearing(), Bearing.class));
            faces.add(face);
        }
        jsonObject.add("faces", faces);
        if (src.getError() == null) {
            jsonObject.addProperty("succeeded", true);
        } else {
            jsonObject.addProperty("error", src.getError().toString());
            jsonObject.addProperty("succeeded", false);
        }
        return jsonObject;
    }
}
