package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

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
        jsonObject.addProperty("expiryTime", src.getMaxDuration(TimeUnit.SECONDS));
        jsonObject.addProperty("numberOfResultsToCollect", src.getFaceCaptureCount());
        jsonObject.addProperty("yawThreshold", src.getYawThreshold());
        jsonObject.addProperty("pitchThreshold", src.getPitchThreshold());
        jsonObject.addProperty("faceWidthFraction", src.getExpectedFaceExtents().getProportionOfViewWidth());
        jsonObject.addProperty("faceHeightFraction", src.getExpectedFaceExtents().getProportionOfViewHeight());
        jsonObject.addProperty("pauseDuration", src.getPauseDuration(TimeUnit.SECONDS));
        jsonObject.addProperty("faceBufferSize", src.getFaceCaptureFaceCount());

        return jsonObject;
    }
}
