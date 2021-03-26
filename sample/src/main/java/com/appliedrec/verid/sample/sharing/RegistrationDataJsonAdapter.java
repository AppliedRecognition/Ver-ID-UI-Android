package com.appliedrec.verid.sample.sharing;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;

import com.appliedrec.verid.core2.RecognizableSubject;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

class RegistrationDataJsonAdapter implements JsonSerializer<RegistrationData>, JsonDeserializer<RegistrationData> {

    @Override
    public JsonElement serialize(RegistrationData src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonArray facesArray = new JsonArray();
        for (RecognizableSubject face : src.getFaceTemplates()) {
            JsonObject faceObject = new JsonObject();
            String template = Base64.encodeToString(face.getRecognitionData(), Base64.NO_WRAP);
            faceObject.addProperty("data", template);
            faceObject.addProperty("version", face.getVersion());
            facesArray.add(faceObject);
        }
        jsonObject.add("faces", facesArray);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (Base64OutputStream base64OutputStream = new Base64OutputStream(outputStream, Base64.NO_WRAP)) {
                src.getProfilePicture().compress(Bitmap.CompressFormat.JPEG, 100, base64OutputStream);
                base64OutputStream.flush();
                String profilePicture = outputStream.toString("UTF-8");
                jsonObject.addProperty("profilePicture", profilePicture);
            }
        } catch (Exception e) {
            throw new JsonIOException(e);
        }
        return jsonObject;
    }

    @Override
    public RegistrationData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonArray facesArray = jsonObject.getAsJsonArray("faces");
        RecognizableSubject[] subjects = new RecognizableSubject[facesArray.size()];
        int i = 0;
        for (JsonElement face : facesArray) {
            JsonObject faceObject = face.getAsJsonObject();
            String templateString = faceObject.get("data").getAsString();
            int version = faceObject.get("version").getAsInt();
            byte[] template = Base64.decode(templateString, Base64.NO_WRAP);
            RecognizableSubject subject = new RecognizableSubject(template, version);
            subjects[i++] = subject;
        }
        RegistrationData registrationData =  new RegistrationData();
        try {
            byte[] picture = jsonObject.get("profilePicture").getAsString().getBytes(StandardCharsets.UTF_8);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(picture)) {
                try (Base64InputStream base64InputStream = new Base64InputStream(inputStream, Base64.NO_WRAP)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(base64InputStream);
                    registrationData.setProfilePicture(bitmap);
                }
            }
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
        registrationData.setFaceTemplates(subjects);
        return registrationData;
    }
}
