package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.test.platform.app.InstrumentationRegistry;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDFactory;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.identity.VerIDIdentity;
import com.appliedrec.verid.ui2.sharing.SessionResultPackage;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionExportTest {

    private VerID verID;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        String password = "e5303959-93d9-421a-b17e-87f71fc3d866";
        VerIDIdentity identity = new VerIDIdentity(context, password);
        VerIDFactory factory = new VerIDFactory(context, identity);
        verID = factory.createVerIDSync();
    }

    @Test
    public void testArchiveSessionResult() throws Exception {
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        VerIDSessionResult result = new VerIDSessionResult(new VerIDSessionException(new Exception("Test")), System.currentTimeMillis()-1000, System.currentTimeMillis(), null);
        SessionResultPackage sessionResultPackage = new SessionResultPackage(verID, sessionSettings,result);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            sessionResultPackage.archiveToStream(outputStream);
            byte[] zipped = outputStream.toByteArray();
            assertTrue(zipped.length > 0);
        }
    }

    @Test
    public void testUnarchiveSessionResult() throws Exception {
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        ArrayList<FaceCapture> faceCaptures = new ArrayList<>();
        for (int i=0; i<sessionSettings.getFaceCaptureCount(); i++) {
            Bearing bearing;
            if (i == 0) {
                bearing = Bearing.STRAIGHT;
            } else {
                Bearing[] bearings = new Bearing[sessionSettings.getBearings().size()-1];
                int j=0;
                for (Bearing b : sessionSettings.getBearings()) {
                    if (b != Bearing.STRAIGHT) {
                        bearings[j++] = b;
                    }
                }
                if (bearings.length > 0) {
                    bearing = bearings[Math.min(bearings.length - 1, (int)(Math.random() * bearings.length))];
                } else {
                    bearing = Bearing.STRAIGHT;
                }
            }
            RecognizableFace face = new RecognizableFace(new Face(new RectF(10, 10, 50, 60), new EulerAngle(0, 0, 0), new PointF(15, 15), new PointF(45, 15), new byte[178], 10f, new PointF[0], new float[10]), new byte[128]);
            Bitmap image = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888);
            faceCaptures.add(new FaceCapture(face, bearing, image));
        }
        VerIDSessionResult result = new VerIDSessionResult(faceCaptures, System.currentTimeMillis()-1000, System.currentTimeMillis(), null);
        SessionResultPackage sessionResultPackage = new SessionResultPackage(verID, sessionSettings,result);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            sessionResultPackage.archiveToStream(outputStream);
            byte[] zipped = outputStream.toByteArray();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipped)) {
                SessionResultPackage received = new SessionResultPackage(InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext(), inputStream);
                assertEquals(sessionResultPackage.getEnvironmentSettings().getConfidenceThreshold(), received.getEnvironmentSettings().getConfidenceThreshold(), 0.01);
                assertEquals(sessionResultPackage.getEnvironmentSettings().getAuthenticationThreshold(), received.getEnvironmentSettings().getAuthenticationThreshold(), 0.01);
                assertEquals(sessionResultPackage.getEnvironmentSettings().getFaceTemplateExtractionThreshold(), received.getEnvironmentSettings().getFaceTemplateExtractionThreshold(), 0.01);
                assertEquals(sessionResultPackage.getEnvironmentSettings().getVeridVersion(), received.getEnvironmentSettings().getVeridVersion());
                assertEquals(sessionResultPackage.getResult().getFaceCaptures().length, received.getResult().getFaceCaptures().length);
                for (int i=0; i < sessionResultPackage.getResult().getFaceCaptures().length; i++) {
                    FaceCapture expectedCapture = sessionResultPackage.getResult().getFaceCaptures()[i];
                    FaceCapture actualCapture = received.getResult().getFaceCaptures()[i];
                    assertArrayEquals(expectedCapture.getFace().getRecognitionData(), actualCapture.getFace().getRecognitionData());
                    assertEquals(expectedCapture.getBearing(), actualCapture.getBearing());
                    assertEquals(expectedCapture.getImage().getWidth(), actualCapture.getImage().getWidth());
                    assertEquals(expectedCapture.getImage().getHeight(), actualCapture.getImage().getHeight());
                }
                assertEquals(sessionResultPackage.getResult().getError().isPresent(), received.getResult().getError().isPresent());
                assertEquals(sessionResultPackage.getResult().getSessionDiagnostics().isPresent(), received.getResult().getSessionDiagnostics().isPresent());
                assertEquals(sessionResultPackage.getResult().getVideoUri().isPresent(), received.getResult().getVideoUri().isPresent());
                assertEquals(sessionResultPackage.getSettings().getFaceCaptureCount(), received.getSettings().getFaceCaptureCount());
                assertEquals(sessionResultPackage.getSettings().getFaceCaptureFaceCount(), received.getSettings().getFaceCaptureFaceCount());
                assertEquals(sessionResultPackage.getSettings().getMaxDuration(TimeUnit.SECONDS), received.getSettings().getMaxDuration(TimeUnit.SECONDS));
                assertEquals(sessionResultPackage.getSettings().getPauseDuration(TimeUnit.SECONDS), received.getSettings().getPauseDuration(TimeUnit.SECONDS));
                assertEquals(sessionResultPackage.getSettings().getExpectedFaceExtents().getProportionOfViewWidth(), received.getSettings().getExpectedFaceExtents().getProportionOfViewWidth(), 0.01);
                assertEquals(sessionResultPackage.getSettings().getExpectedFaceExtents().getProportionOfViewHeight(), received.getSettings().getExpectedFaceExtents().getProportionOfViewHeight(), 0.01);
                assertEquals(sessionResultPackage.getSettings().getYawThreshold(), received.getSettings().getYawThreshold(), 0.01);
                assertEquals(sessionResultPackage.getSettings().getPitchThreshold(), received.getSettings().getPitchThreshold(), 0.01);
            }
        }
    }
}
