package com.appliedrec.verid.sample.sharing;

import com.appliedrec.verid.core2.serialization.CborEncoder;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

class EnvironmentSettingsJsonAdapter implements JsonSerializer<EnvironmentSettings>, CborEncoder<EnvironmentSettings> {
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
    public void encodeToCbor(EnvironmentSettings src, CBORGenerator cborGenerator) throws Exception {
        cborGenerator.writeStartObject();
        cborGenerator.writeNumberField("confidenceThreshold", src.getConfidenceThreshold());
        cborGenerator.writeNumberField("faceTemplateExtractionThreshold", src.getFaceTemplateExtractionThreshold());
        cborGenerator.writeNumberField("authenticationThreshold", src.getAuthenticationThreshold());
        cborGenerator.writeStringField("veridVersion", src.getVeridVersion());
        cborGenerator.writeStringField("os", "Android");
        cborGenerator.writeEndObject();
    }
}
