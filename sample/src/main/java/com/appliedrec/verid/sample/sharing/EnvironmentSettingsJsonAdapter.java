package com.appliedrec.verid.sample.sharing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

class EnvironmentSettingsJsonAdapter implements JsonSerializer<EnvironmentSettings> {
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
}
