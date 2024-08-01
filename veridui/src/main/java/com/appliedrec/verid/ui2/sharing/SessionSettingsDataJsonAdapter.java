package com.appliedrec.verid.ui2.sharing;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

public class SessionSettingsDataJsonAdapter implements JsonSerializer<VerIDSessionSettings>, JsonDeserializer<VerIDSessionSettings> {

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
        jsonObject.addProperty("maxDuration", src.getMaxDuration(TimeUnit.SECONDS));
        jsonObject.addProperty("faceCaptureCount", src.getFaceCaptureCount());
        jsonObject.addProperty("yawThreshold", src.getYawThreshold());
        jsonObject.addProperty("pitchThreshold", src.getPitchThreshold());
        jsonObject.addProperty("faceWidthFraction", src.getExpectedFaceExtents().getProportionOfViewWidth());
        jsonObject.addProperty("faceHeightFraction", src.getExpectedFaceExtents().getProportionOfViewHeight());
        jsonObject.addProperty("pauseDuration", src.getPauseDuration(TimeUnit.SECONDS));
        jsonObject.addProperty("faceCaptureFaceCount", src.getFaceCaptureFaceCount());

        return jsonObject;
    }

    @Override
    public VerIDSessionSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        VerIDSessionSettings sessionSettings = null;
        Bearing[] bearings = null;
        EnumSet<Bearing> bearingSet = EnumSet.noneOf(Bearing.class);
        if (jsonObject.has("bearings")) {
            bearings = context.deserialize(jsonObject.get("bearings"), Bearing[].class);
            bearingSet.addAll(Arrays.asList(bearings));
        }
        if (jsonObject.has("type")) {
            String type = jsonObject.get("type").getAsString();
            if (type.equals("authentication")) {
                AuthenticationSessionSettings authenticationSessionSettings = new AuthenticationSessionSettings();
                if (!bearingSet.isEmpty()) {
                    authenticationSessionSettings.setBearings(bearingSet);
                }
                sessionSettings = authenticationSessionSettings;
            } else if (type.equals("registration")) {
                RegistrationSessionSettings registrationSessionSettings = new RegistrationSessionSettings();
                if (bearings != null) {
                    registrationSessionSettings.setBearingsToRegister(bearings);
                }
                sessionSettings = registrationSessionSettings;
            } else {
                LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
                if (!bearingSet.isEmpty()) {
                    livenessDetectionSessionSettings.setBearings(bearingSet);
                }
                sessionSettings = livenessDetectionSessionSettings;
            }
        } else {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            if (!bearingSet.isEmpty()) {
                livenessDetectionSessionSettings.setBearings(bearingSet);
            }
            sessionSettings = livenessDetectionSessionSettings;
        }
        sessionSettings.setMaxDuration(jsonObject.get("maxDuration").getAsLong(), TimeUnit.SECONDS);
        sessionSettings.setFaceCaptureCount(jsonObject.get("faceCaptureCount").getAsInt());
        sessionSettings.setYawThreshold(jsonObject.get("yawThreshold").getAsFloat());
        sessionSettings.setPitchThreshold(jsonObject.get("pitchThreshold").getAsFloat());
        float faceWidthFraction = jsonObject.get("faceWidthFraction").getAsFloat();
        float faceHeightFraction = jsonObject.get("faceHeightFraction").getAsFloat();
        sessionSettings.setExpectedFaceExtents(new FaceExtents(faceWidthFraction, faceHeightFraction));
        sessionSettings.setPauseDuration(jsonObject.get("pauseDuration").getAsLong(), TimeUnit.SECONDS);
        sessionSettings.setFaceCaptureFaceCount(jsonObject.get("faceCaptureFaceCount").getAsInt());
        return sessionSettings;
    }
}
