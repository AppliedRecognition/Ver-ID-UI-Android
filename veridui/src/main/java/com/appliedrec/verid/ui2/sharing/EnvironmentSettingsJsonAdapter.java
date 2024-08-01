package com.appliedrec.verid.ui2.sharing;

import android.os.Build;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class EnvironmentSettingsJsonAdapter implements JsonSerializer<EnvironmentSettings>, JsonDeserializer<EnvironmentSettings> {
    @Override
    public JsonElement serialize(EnvironmentSettings src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("faceDetectorVersion", src.getFaceDetectorVersion());
        jsonObject.addProperty("confidenceThreshold", src.getConfidenceThreshold());
        jsonObject.addProperty("faceTemplateExtractionThreshold", src.getFaceTemplateExtractionThreshold());
        jsonObject.addProperty("authenticationThreshold", src.getAuthenticationThreshold());
        jsonObject.addProperty("veridVersion", src.getVeridVersion());
        jsonObject.addProperty("applicationVersion", src.getApplicationVersion());
        jsonObject.addProperty("applicationId", src.getApplicationId());
        jsonObject.addProperty("os", src.getOs());
        jsonObject.addProperty("deviceModel", src.getDeviceModel());
        return jsonObject;
    }

    @Override
    public EnvironmentSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        int faceDetectorVersion = jsonObject.get("faceDetectorVersion").getAsInt();
        float confidenceThreshold = jsonObject.get("confidenceThreshold").getAsFloat();
        float faceTemplateExtractionThreshold = jsonObject.get("faceTemplateExtractionThreshold").getAsFloat();
        float authenticationThreshold = jsonObject.get("authenticationThreshold").getAsFloat();
        String veridVersion = jsonObject.get("veridVersion").getAsString();
        String applicationId = jsonObject.get("applicationId").getAsString();
        String applicationVersion = jsonObject.get("applicationVersion").getAsString();
        String os = jsonObject.get("os").getAsString();
        String deviceModel = jsonObject.get("deviceModel").getAsString();
        return new EnvironmentSettings(faceDetectorVersion, confidenceThreshold, faceTemplateExtractionThreshold, authenticationThreshold, veridVersion, applicationId, applicationVersion, deviceModel, os);
    }
}
