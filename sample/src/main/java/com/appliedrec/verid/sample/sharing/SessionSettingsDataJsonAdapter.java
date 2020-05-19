package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.EnumSet;

class SessionSettingsDataJsonAdapter implements JsonSerializer<VerIDSessionSettings> {

    @Override
    public JsonElement serialize(VerIDSessionSettings src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        if (src instanceof AuthenticationSessionSettings) {
            jsonObject.addProperty("type", "authentication");
            jsonObject.add("bearings", context.serialize(((AuthenticationSessionSettings)src).getBearings(), EnumSet.class));
        } else if (src instanceof RegistrationSessionSettings) {
            jsonObject.addProperty("type", "registration");
            jsonObject.add("bearings", context.serialize(((RegistrationSessionSettings)src).getBearingsToRegister(), Bearing[].class));
        } else if (src instanceof LivenessDetectionSessionSettings) {
            jsonObject.addProperty("type", "liveness detection");
            jsonObject.add("bearings", context.serialize(((LivenessDetectionSessionSettings)src).getBearings(), EnumSet.class));
        }
        jsonObject.addProperty("expiryTime", src.getExpiryTime()/1000);
        jsonObject.addProperty("numberOfResultsToCollect", src.getNumberOfResultsToCollect());
        jsonObject.addProperty("useBackCamera", src.getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.BACK);
        jsonObject.addProperty("maxRetryCount", src.getMaxRetryCount());
        jsonObject.addProperty("yawThreshold", src.getYawThreshold());
        jsonObject.addProperty("pitchThreshold", src.getPitchThreshold());
        jsonObject.addProperty("speakPrompts", src.shouldSpeakPrompts());
        jsonObject.addProperty("faceWidthFraction", src.getFaceBoundsFraction().x);
        jsonObject.addProperty("faceHeightFraction", src.getFaceBoundsFraction().y);
        jsonObject.addProperty("pauseDuration", src.getPauseDuration()/1000);
        jsonObject.addProperty("faceBufferSize", src.getFaceBufferSize());
        jsonObject.addProperty("extractFaceTemplates", src.getIncludeFaceTemplatesInResult());

        return jsonObject;
    }
}
