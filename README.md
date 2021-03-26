![Maven metadata URL](https://img.shields.io/maven-metadata/v/https/dev.ver-id.com/artifactory/gradle-release/com/appliedrec/verid/ui2/maven-metadata.xml.svg)

# Ver-ID UI for Android

## What's new in Ver-ID 2.0

Version 2 of the Ver-ID SDK brings a number of improvements:

- Simpler API
    - Consumers no longer have to parse session results in activities' `onActivityResult`.
    - Session result is passed to the session delegate.
    - Session delegate now includes optional methods that allow better session customisation.
    - Instead of aligning the Android and iOS APIs, we decided to use platform conventions. For example, `veridSessionDidFinishWithResult` became `onSessionFinished` on Android.
    - Introduced reactive stream implementations in some classes. For example `VerIDFactory` now extends [`RxJava.Single`](http://reactivex.io/documentation/single.html).
- Improved performance
    - Faster camera preview using newer camera APIs.
    - Face capture images are no longer written to files, saving disk space and eliminating unwanted cached files.
    - Results are passed directly to the session delegate without having to be marshalled into parcels.
- Less ambiguity
    - Better defined error codes – Ver-ID methods and sessions throw or return one of the pre-defined exceptions with clearly-defined error codes.
    - Better separation of UI and core session logic.
    - Using Java 8 optionals – cleaner and less ambiguous than `null` checks.
- Diagnostic features
    - Sessions can be configured to return diagnostic data to facilitate debugging.
    - Added face covering detection.
    - Added reporting of image quality metrics.

Please note that Ver-ID 2.+ is not compatible with Ver-ID 1.+. You will need to migrate to the new SDK. 

We opted to separate the new API to its own packages so both Ver-ID 1.+ and Ver-ID 2.+ can co-exist in the same project.

## Requirements

To build this project and to run the sample app you will need a computer with these applications:

- [Android Studio 4](https://developer.android.com/studio) with Gradle plugin version 4.0.0 or newer
- [Git](https://git-scm.com)

## Installation

1. Open a shell console and enter the following commands:

	~~~shell
	git clone https://github.com/AppliedRecognition/Ver-ID-UI-Android.git
	~~~
1. Open Android Studio and from the top menu select **File/Open...**. Navigate to the directory where you checked out the Git project and press **Open**.
1. Connect your Android device for debugging via USB.
1. In the main menu select **Run/Run 'sample'**. Select your Android device and press **OK**.

## Adding Ver-ID to your own project

1. [Register your app](https://dev.ver-id.com/licensing/). You will need your app's package name.
2. Registering your app will generate an evaluation licence for your app. The licence is valid for 30 days. If you need a production licence please [contact Applied Recognition](mailto:sales@appliedrec.com).
2. When you finish the registration you'll receive a file called **Ver-ID identity.p12** and a password. Copy the password to a secure location.
3. Copy the **Ver-ID identity.p12** into your app's assets folder. A common location is **your\_app_module/src/main/assets**.
8. Ver-ID will need the password you received at registration.
    - You can either specify the password when you create an instance of `VerIDSDKIdentity` that you pass to `VerIDFactory`:

        ~~~java
        try {
            VerIDSDKIdentity identity = new VerIDSDKIdentity(this, "your password goes here");
            VerIDFactory veridFactory = new VerIDFactory(this, identity);
        } catch (Exception e) {
            // Failed to create identity with your credentials.
        }
        ~~~
    - Or you can add the password in your app's **AndroidManifest.xml**:

        ~~~xml
        <manifest>
            <application>
                <meta-data
                    android:name="com.appliedrec.verid.password"
                    android:value="your password goes here" />
            </application>
        </manifest>
        ~~~
        and construct your identity without specifying the password:
        
        ~~~java
        try {
            VerIDSDKIdentity identity = new VerIDSDKIdentity(this);
            VerIDFactory veridFactory = new VerIDFactory(this, identity);
        } catch (Exception e) {
            // Failed to create identity with your credentials.
        }
        ~~~
    - Constructing `VerIDFactory` without an instance of `VerIDSDKIdentity` assumes that the **Ver-ID identity.p12** file is in the app's **assets** folder and the password is in the **AndroidManifest.xml**.
1. Add the following entries in your app module's **gradle.build** file:
    
    ~~~groovy
    repositories {
        maven {
            url 'https://dev.ver-id.com/artifactory/gradle-release'
        }
    }
    android {
        defaultConfig {
            multiDexEnabled true
        }
        compileOptions {
            coreLibraryDesugaringEnabled true
            sourceCompatibility JavaVersion.VERSION_1_8
            targetCompatibility JavaVersion.VERSION_1_8
        }
    }
    dependencies {
        coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:1.0.9'
	    implementation 'com.appliedrec.verid:ui:2.0.0-beta.01'
    }
    ~~~
    
## Migrating from Ver-ID 1
Please consult [this document](Migrating from Ver-ID 1 to Ver-ID 2.md).
		
## Usage

### Creating Ver-ID Environment
Prior to running Ver-ID sessions you will need to create an instance of Ver-ID.

~~~java
VerIDFactory verIDFactory = new VerIDFactory(getContext(), new VerIDFactoryDelegate() {
    @Override
    public void onVerIDCreated(VerIDFactory verIDFactory, VerID verID) {
        // You can now use the VerID instance
    }

    @Override
    public void onVerIDCreationFailed(VerIDFactory verIDFactory, Exception e) {
        // Failed to create an instance of Ver-ID
    }
});
verIDFactory.createVerID();
~~~
`VerIDFactory` extends the reactive class `Single`. As an alternative to using `VerIDFactoryDelegate` you can use the reactive implementation:

~~~java
VerIDFactor verIDFactory = new VerIDFactory(getContext());
Disposable veridFactoryDisposable = verIDFactory
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        verID -> {
            // You can now use the VerID instance
        },
        error -> {
            // Failed to create an instance of Ver-ID
        }
    );
// Call veridFactoryDisposable.dispose() to cancel the operation.
~~~

### Running Ver-ID Session Activities
~~~java
class MyActivity extends AppCompatActivity implements VerIDFactoryDelegate, VerIDSessionDelegate {

    void startLivenessDetectionSession() {
        VerIDFactory veridFactory = new VerIDFactory(this);
        veridFactory.setDelegate(this);
        veridFactory.createVerID();
    }
    
    //region Ver-ID factory delegate
    
    @Override
    public void onVerIDCreated(VerIDFactory verIDFactory, VerID verID) {
        // You can now start a Ver-ID session
        LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
        VerIDSession session = new VerIDSession(verID, settings);
        session.setDelegate(this);
        session.start();
    }
    
    @Override
    public void onVerIDCreationFailed(VerIDFactory verIDFactory, Exception e) {
        // Failed to create an instance of Ver-ID
    }
    
    //endregion
    
    //region Ver-ID session delegate
    
    @Override
    public void onSessionFinished(IVerIDSession<?> session, VerIDSessionResult result) {
        if (!result.getError().isPresent()) {
            // Session succeeded
        } else {
            // Session failed
        }
    }
    
    // The following delegate methods are optional

    // Implement this method to notify your app when the user cancelled the session
    @Override    
    public void onSessionCanceled(IVerIDSession<?> session) {
    }

    // Return true to show the result of the session in a new activity
    @Override
    public boolean shouldSessionDisplayResult(IVerIDSession<?> session, VerIDSessionResult result) {
        return true;
    }

    // Return true for the session to use audible prompts in addition to on-screen instructions
    @Override
    public boolean shouldSessionSpeakPrompts(IVerIDSession<?> session) {
        return true;
    }

    // Return CameraLocation.BACK to use the back-facing camera instead of the default front-facing (selfie) camera
    @Override
    public CameraLocation getSessionCameraLocation(IVerIDSession<?> session) {
        return CameraLocation.BACK;
    }

    // Return true to record a video of the session
    @Override
    public boolean shouldSessionRecordVideo(IVerIDSession<?> session) {
        return true;
    }

    // Override this method if you wish to implement your own image iterator/reader class
    @Override
    public Function<VerID, IImageIterator> createImageIteratorFactory(IVerIDSession<?> session) {
        return VerIDImageIterator::new;
    }

    // Override to supply your own dialog when the session fails and the user is allowed to re-run the session
    @Override
    public SessionFailureDialogFactory createSessionFailureDialogFactory(IVerIDSession<?> session) {
        return new DefaultSessionFailureDialogFactory();
    }

    // Override to supply your own session view
    @Override
    public <V extends View & ISessionView> Function<Context, V> createSessionViewFactory(IVerIDSession<?> session) {
        return context -> (V) new SessionView(context);
    }

    // Override to supply custom functions to control the session logic
    @Override
    public SessionFunctions createSessionFunctions(IVerIDSession<?> session, VerID verID, VerIDSessionSettings sessionSettings) {
        return new SessionFunctions(verID, sessionSettings);
    }

    // Override to supply a custom session activity
    @Override
    public <A extends Activity & ISessionActivity> Class<A> getSessionActivityClass(IVerIDSession<?> session) {
        return (Class<A>) SessionActivity.class;
    }

    // Override to supply custom activities for displaying the session results
    @Override
    public <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(IVerIDSession<?> session, VerIDSessionResult result) {
        if (result.getError().isPresent()) {
            return (Class<A>) SessionSuccessActivity.class;
        } else {
            return (Class<A>) SessionFailureActivity.class;
        }
    }
    
    // Override to allow the user retry the session
    // You can supply your own logic. In this example the delegate keeps track of how many times the session ran (runCount)
    // and lets the user retry if the session ran fewer than 3 times
    @Override
    public boolean shouldRetrySessionAfterFailure(IVerIDSession<?> session, VerIDSessionException exception) {
        return runCount < 3;
    }
    
    //endregion
}
~~~

### Controlling Liveness Detection
If you run a Ver-ID session with `LivenessDetectionSessionSettings` or its subclass `AuthenticationSessionSettings` you can control how Ver-ID detects liveness.

To disable liveness detection set the session's [face capture count](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core2.session.VerIDSessionSettings.html#setFaceCaptureCount(int)) to `1`.

To control the bearings the user may be asked to assume call the [setBearings(EnumSet)](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings.html#setBearings(EnumSet)) method. For example, to ask the user to look straight at the camera and then assume 2 random poses from the choice of left and right modify the settings as follows:

~~~java
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
settings.setFaceCaptureCount(3); // 1 straight plus 2 other poses
settings.setBearings(EnumSet.of(Bearing.STRAIGHT, Bearing.LEFT, Bearing.RIGHT)); // Limit the poses to left and right
~~~
The session result will contain 3 faces: 1 looking straight at the camera and 2 in random poses.

### Replacing Components in the Ver-ID Environment
You can have Ver-ID your own user management (face template storage) layer or even your own face detection and face recognition. To do that create an appropriate factory class and set it on the Ver-ID factory before calling the `createVerID()` method.

For example, to add your own storage layer:

~~~java
VerIDFactory verIDFactory = new VerIDFactory(this, this);
IUserManagementFactory userManagementFactory = new IUserManagementFactory() {
    @Override
    public IUserManagement createUserManagement() throws Exception {
        return new IUserManagement() {
            @Override
            public void assignFacesToUser(IRecognizable[] faces, String userId) throws Exception {
                
            }

            @Override
            public void deleteFaces(IRecognizable[] faces) throws Exception {

            }

            @Override
            public String[] getUsers() throws Exception {
                return new String[0];
            }

            @Override
            public IRecognizable[] getFacesOfUser(String userId) throws Exception {
                return new IRecognizable[0];
            }

            @Override
            public IRecognizable[] getFaces() throws Exception {
                return new IRecognizable[0];
            }

            @Override
            public void deleteUsers(String[] userIds) throws Exception {

            }

            @Override
            public void close() {

            }
        };
    }
};
verIDFactory.setUserManagementFactory(userManagementFactory);
verIDFactory.createVerID();
~~~

Full documentation available on the project's [Github page](https://appliedrecognition.github.io/Ver-ID-UI-Android/).

## Change log
Changes are described in release notes.
