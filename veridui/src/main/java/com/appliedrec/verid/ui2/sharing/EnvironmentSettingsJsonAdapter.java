package com.appliedrec.verid.ui2.sharing;

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
        jsonObject.addProperty("confidenceThreshold", src.getConfidenceThreshold());
        jsonObject.addProperty("faceTemplateExtractionThreshold", src.getFaceTemplateExtractionThreshold());
        jsonObject.addProperty("authenticationThreshold", src.getAuthenticationThreshold());
        jsonObject.addProperty("veridVersion", src.getVeridVersion());
        jsonObject.addProperty("os", "Android");
        return jsonObject;
    }

    @Override
    public EnvironmentSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        float confidenceThreshold = jsonObject.get("confidenceThreshold").getAsFloat();
        float faceTemplateExtractionThreshold = jsonObject.get("faceTemplateExtractionThreshold").getAsFloat();
        float authenticationThreshold = jsonObject.get("authenticationThreshold").getAsFloat();
        String veridVersion = jsonObject.get("veridVersion").getAsString();
        return new EnvironmentSettings(confidenceThreshold, faceTemplateExtractionThreshold, authenticationThreshold, veridVersion);
    }
}
