package com.appliedrec.verid.sample;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.appliedrec.verid.core2.AntiSpoofingException;
import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.Image;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.VerIDImageBitmap;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.FaceAlignmentDetection;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.FacePresenceDetection;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.SessionFaceTracking;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.SpoofingDetection;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;
import com.appliedrec.verid.ui2.SessionActivity;
import com.appliedrec.verid.ui2.VerIDSession;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import io.reactivex.rxjava3.functions.Function;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.init;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.Intents.release;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withChild;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class SampleAppTest {

    @After
    public void unregisterIdlingResources() {
        for (IdlingResource idlingResource : IdlingRegistry.getInstance().getResources()) {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }
    }

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Rule
    public IntentsTestRule<MainActivity> mainActivityIntentsTestRule = new IntentsTestRule<>(MainActivity.class, true, false);

    @Rule
    public IntentsTestRule<IntroActivity> introActivityIntentsTestRule = new IntentsTestRule<>(IntroActivity.class, true, false);

    @Rule
    public IntentsTestRule<RegisteredUserActivity> registeredUserActivityIntentsTestRule = new IntentsTestRule<>(RegisteredUserActivity.class, true, false);

    @Rule
    public IntentsTestRule<RegistrationImportReviewActivity> registrationImportReviewActivityIntentsTestRule = new IntentsTestRule<>(RegistrationImportReviewActivity.class, true, false);

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.appliedrec.verid.sample", appContext.getPackageName());
    }

    @Test
    public void test_startAppFirstTime_showIntroPage() {
        mainActivityIntentsTestRule.launchActivity(null);
        IdlingRegistry.getInstance().register(mainActivityIntentsTestRule.getActivity());
        Espresso.onIdle();
        intended(hasComponent(IntroActivity.class.getName()));
    }

    @Test
    public void test_importRegistration_requestFile() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(IntroActivity.EXTRA_SHOW_REGISTRATION, true);
        introActivityIntentsTestRule.launchActivity(intent);
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
        onView(withText(InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.import_registration))).perform(click());
        intended(hasAction(Intent.ACTION_GET_CONTENT));
    }

    @Test
    public void test_reviewRegistrationImport() throws Exception {
        try (InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("SampleRegistration.verid")) {
            File file = File.createTempFile("registration_", ".verid");
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                int read;
                byte[] buffer = new byte[512];
                while ((read = inputStream.read(buffer, 0, buffer.length)) > 0) {
                    fileOutputStream.write(buffer, 0, read);
                }
                fileOutputStream.flush();
                Intent registrationData = new Intent();
                registrationData.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                registrationImportReviewActivityIntentsTestRule.launchActivity(registrationData);
                onView(withId(R.id.button)).check(matches(isDisplayed()));
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void test_importRegistration() throws Exception {
        try (InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("SampleRegistration.verid")) {
            File file = File.createTempFile("registration_", ".verid");
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                int read;
                byte[] buffer = new byte[512];
                while ((read = inputStream.read(buffer, 0, buffer.length)) > 0) {
                    fileOutputStream.write(buffer, 0, read);
                }
                fileOutputStream.flush();
                Intent registrationData = new Intent();
                registrationData.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

                mainActivityIntentsTestRule.launchActivity(null);
                IdlingRegistry.getInstance().register(mainActivityIntentsTestRule.getActivity());
                Espresso.onIdle();
                intended(hasComponent(IntroActivity.class.getName()));
                openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
                intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, registrationData));
                onView(withText(InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.import_registration))).perform(click());
                onView(withId(R.id.button)).perform(click());
                Espresso.onIdle();
                intended(hasComponent(RegisteredUserActivity.class.getName()));
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void test_startAuthentication() throws Exception {
        test_importRegistration();
        onView(withId(R.id.authenticate)).perform(click());
        onView(withText("English")).perform(click());
        intended(hasComponent(SessionActivity.class.getName()));
    }

    @Test
    public void test_authenticateWithWrongFace_fails() throws Exception {
        AuthenticationSessionSettings sessionSettings = new AuthenticationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        runSession(getSampleAppVerID(), sessionSettings, new VerIDSessionException(new VerIDCoreException(VerIDCoreException.Code.AUTHENTICATION_SCORE_TOO_LOW), VerIDSessionException.Code.FACE_DETECTION_EVALUATION_ERROR));
        intended(hasComponent(SessionResultActivity.class.getName()));
        onView(allOf(withId(R.id.value), withText("No"), withParent(withChild(allOf(withId(R.id.key),withText("Succeeded")))))).check(matches(isDisplayed()));
    }

    @Test
    public void test_registerWithFailure_showsFailurePage() throws Exception {
        RegistrationSessionSettings sessionSettings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        runSession(getSampleAppVerID(), sessionSettings, new VerIDSessionException(VerIDSessionException.Code.FACE_DETECTION_EVALUATION_ERROR));
        intended(hasComponent(SessionResultActivity.class.getName()));
        onView(allOf(withId(R.id.value), withText("No"), withParent(withChild(allOf(withId(R.id.key),withText("Succeeded")))))).check(matches(isDisplayed()));
    }

    @Test
    public void test_authenticate_showsResult() throws Exception {
        VerID verID = getSampleAppVerID();
        VerIDImageBitmap image = createVerIDImage();
        Face[] faces = verID.getFaceDetection().detectFacesInImage(image, 1, 0);
        assertEquals(1, faces.length);
        RecognizableFace[] recognizableFaces = verID.getFaceRecognition().createRecognizableFacesFromFaces(faces, image);
        verID.getUserManagement().assignFacesToUser(recognizableFaces, VerIDUser.DEFAULT_USER_ID);
        AuthenticationSessionSettings sessionSettings = new AuthenticationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        runSession(verID, sessionSettings, null);
        intended(hasComponent(SessionResultActivity.class.getName()));
        onView(allOf(withId(R.id.value), withText("Yes"), withParent(withChild(allOf(withId(R.id.key),withText("Succeeded")))))).check(matches(isDisplayed()));
    }

    @Test
    public void test_showSettings_succeeds() {
        Intent intent = new Intent();
        intent.putExtra(IntroActivity.EXTRA_SHOW_REGISTRATION, true);
        introActivityIntentsTestRule.launchActivity(intent);
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
        onView(withText(R.string.settings)).perform(click());
        intended(hasComponent(SettingsActivity.class.getName()));
    }

    @Test
    public void test_registerMoreFaces_launchesRegistrationSession() throws Exception {
        test_importRegistration();
        release();
        init();
        onView(withId(R.id.register)).perform(click());
        intended(hasComponent(SessionActivity.class.getName()));
    }

    @Test
    public void test_unregisterUser_showsIntroActivity() throws Exception {
        test_importRegistration();
        release();
        init();
        onView(withId(R.id.removeButton)).perform(click());
        onView(withText(R.string.unregister)).perform(click());
        intended(hasComponent(IntroActivity.class.getName()));
    }

    @Test
    public void test_exportRegistration_launchesChooser() throws Exception {
        test_importRegistration();
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
        onView(withText(R.string.export_registration)).perform(click());
        intended(hasAction(Intent.ACTION_CHOOSER));
    }

    @Test
    public void test_runSuccessfulSession_showsSuccessActivity() throws Exception {
        runSession(getSampleAppVerID(), getLivenessDetectionSessionSettings(2), null);
        intended(hasComponent(SessionResultActivity.class.getName()));
        onView(allOf(withId(R.id.value), withText("Yes"), withParent(withChild(allOf(withId(R.id.key),withText("Succeeded")))))).check(matches(isDisplayed()));
    }

    @Test
    public void test_runSessionWithAntiSpoofingFailure_showsRetryActivity() throws Exception {
        VerIDSession<LivenessDetectionSessionSettings> session = runSession(getSampleAppVerID(), getLivenessDetectionSessionSettings(1), new VerIDSessionException(new AntiSpoofingException(AntiSpoofingException.Code.MOVED_OPPOSITE, Bearing.STRAIGHT), VerIDSessionException.Code.LIVENESS_FAILURE));
        intended(hasComponent(SessionLivenessDetectionFailureActivity.class.getName()));
    }

    private <T extends VerIDSessionSettings> VerIDSession<T> runSession(VerID verID, T settings, VerIDSessionException exception) {
        registeredUserActivityIntentsTestRule.launchActivity(null);
        VerIDSession session = new VerIDSession(verID, settings);
        SessionFunctions sessionFunctions = new SessionFunctions(verID, settings) {

            @Override
            public Function<Pair<Image, FaceBounds>, SessionFaceTracking> getSessionFaceTrackingAccumulator() {
                return imageFaceBoundsPair -> {
                    getSessionFaceTracking().image = imageFaceBoundsPair.first;
                    getSessionFaceTracking().defaultFaceBounds = imageFaceBoundsPair.second;
                    return getSessionFaceTracking();
                };
            }

            @Override
            public Function<SessionFaceTracking, FacePresenceDetection> getFacePresenceDetectionAccumulator() {
                return sessionFaceTracking -> {
                    getFacePresenceDetection().setStatus(FacePresenceDetection.Status.FOUND);
                    return getFacePresenceDetection();
                };
            }

            @Override
            public Function<FacePresenceDetection, FaceAlignmentDetection> getFaceAlignmentDetectionAccumulator() {
                return facePresenceDetection -> {
                    getFaceAlignmentDetection().setStatus(FaceAlignmentDetection.Status.ALIGNED);
                    return getFaceAlignmentDetection();
                };
            }

            @Override
            public Function<FaceAlignmentDetection, SpoofingDetection> getSpoofingDetectionAccumulator() {
                return faceAlignmentDetection -> getSpoofingDetection();
            }

            @Override
            public FaceDetectionResult createFaceDetectionResult(SpoofingDetection spoofingDetection) {
                return SampleAppTest.this.createFaceDetectionResult(verID, getSessionFaceTracking().requestedBearing);
            }

            @Override
            public FaceCapture createFaceCapture(FaceDetectionResult faceDetectionResult) throws VerIDSessionException {
                return super.createFaceCapture(faceDetectionResult);
            }

            @Override
            public VerIDSessionResult processSessionResult(VerIDSessionResult result) throws VerIDSessionException {
                if (exception != null) {
                    throw exception;
                }
                return super.processSessionResult(result);
            }
        };
        session.setDelegate(registeredUserActivityIntentsTestRule.getActivity());
        session.setSessionFunctions(sessionFunctions);
        session.start();
        IdlingRegistry.getInstance().register(session);
        Espresso.onIdle();
        IdlingRegistry.getInstance().unregister(session);
        return session;
    }

    private LivenessDetectionSessionSettings getLivenessDetectionSessionSettings(int maxRetryCount) {
        LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
        settings.setMaxRetryCount(maxRetryCount);
        return settings;
    }

    private SampleApplication getSampleApplication() {
        return (SampleApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    }

    private VerID getSampleAppVerID() {
        mainActivityIntentsTestRule.launchActivity(null);
        IdlingRegistry.getInstance().register(mainActivityIntentsTestRule.getActivity());
        Espresso.onIdle();
        assertTrue(getSampleApplication().getVerID().isPresent());
        VerID verID = getSampleApplication().getVerID().get();
        mainActivityIntentsTestRule.finishActivity();
        return verID;
    }

    private VerIDImageBitmap createVerIDImage() {
        try {
            AssetManager assetManager = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
            Bitmap bitmap;
            int orientation;
            String imageFileName = "testImage1.jpg";
            try (InputStream inputStream = assetManager.open(imageFileName)) {
                bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) {
                    throw new VerIDSessionException(VerIDSessionException.Code.OTHER);
                }
            }
            try (InputStream inputStream = assetManager.open(imageFileName)) {
                ExifInterface exifInterface = new ExifInterface(inputStream);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
            return new VerIDImageBitmap(bitmap, orientation);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create image");
        }
    }

    private FaceDetectionResult createFaceDetectionResult(VerID verID, Bearing requestedBearing) {
        FaceDetectionResult faceDetectionResult = new FaceDetectionResult(FaceDetectionStatus.FACE_ALIGNED, requestedBearing);
        Image image = createVerIDImage().provideVerIDImage();
        faceDetectionResult.setImage(image);
        try {
            Face[] faces = verID.getFaceDetection().detectFacesInImage(image, 1, 0);
            if (faces.length == 0) {
                throw new VerIDSessionException(VerIDSessionException.Code.OTHER);
            }
            faceDetectionResult.setFace(faces[0]);
            faceDetectionResult.setDefaultFaceBounds(new FaceBounds(new Size(image.getUprightWidth(), image.getUprightHeight()), FaceExtents.DEFAULT));
        } catch (Exception e) {
            faceDetectionResult.setStatus(FaceDetectionStatus.FAILED);
        }
        return faceDetectionResult;
    }
}
