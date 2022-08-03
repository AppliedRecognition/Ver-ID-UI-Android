package com.appliedrec.verid.sample.sharing;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.appliedrec.verid.core2.IRecognizable;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.proto.FaceTemplate;
import com.appliedrec.verid.proto.Registration;
import com.appliedrec.verid.sample.ProfilePhotoHelper;
import com.appliedrec.verid.sample.VerIDUser;
import com.appliedrec.verid.serialization.ProtobufTypeConverter;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class RegistrationExport {

    private final VerID verID;
    private final ProfilePhotoHelper profilePhotoHelper;

    public RegistrationExport(@NonNull VerID verid, @NonNull ProfilePhotoHelper profilePhotoHelper) {
        this.verID = verid;
        this.profilePhotoHelper = profilePhotoHelper;
    }

    @WorkerThread
    public void writeToUri(Uri uri) throws VerIDCoreException, IOException {
        Registration registration = createRegistrationData();
        try (OutputStream outputStream = verID.getContext().get().getContentResolver().openOutputStream(uri)) {
            registration.writeTo(outputStream);
        }
    }

    @WorkerThread
    private Registration createRegistrationData() throws IOException, VerIDCoreException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Bitmap photo = profilePhotoHelper.getProfilePhoto();
            photo.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            ProtobufTypeConverter converter = new ProtobufTypeConverter();
            Registration.Builder builder = Registration.newBuilder()
                    .setImage(ByteString.copyFrom(outputStream.toByteArray()))
                    .setSystemInfo(converter.getCurrentSystemInfo(verID));
            IRecognizable[] faces = verID.getUserManagement().getFacesOfUser(VerIDUser.DEFAULT_USER_ID);
            for (IRecognizable face : faces) {
                builder.addFaces(FaceTemplate.newBuilder().setData(ByteString.copyFrom(face.getRecognitionData())).setVersion(face.getVersion()));
            }
            return builder.build();
        }
    }
}
