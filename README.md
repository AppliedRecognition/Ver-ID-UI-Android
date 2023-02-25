![Maven Central](https://img.shields.io/maven-central/v/com.appliedrec.verid/ui2)

# Ver-ID UI for Android

<details>
<summary>What's new in Ver-ID 2.8</summary>

## Resources delivered separately from the SDK

The Ver-ID SDK's resources are no longer packaged in the SDK's aar file. You have 2 options:

1. Let Ver-ID download the resources first time it loads (default).
    
    - When you call `VerIDFactory.createVerID()` the resource files will be downloaded from a CDN and cached on the device.
    - Next time Ver-ID is loaded it will use the cached resource files.
2. Package the resources with your app.

    - Create a folder called Ver-ID-Models inside your app's assets folder.
    - Copy the content of the [files folder](https://github.com/AppliedRecognition/Ver-ID-Models/tree/master/files) into the Ver-ID-Models folder in your app's assets.
    - Ver-ID will pick up the resource files from assets and copy them into the app's cache.
    - Next time Ver-ID is loaded it will use the resource files from the app's cache.

### Copy only the resources you need

If you are only using specific face template versions in your app you can select the model files pertinent to these versions and omit the unused resource files.

1. First, specify the face template versions you will use in your app. In this example, your app will only use face template versions 16 and 24:
    
    ```java
    // Create Ver-ID factory
    VerIDFactory veridFactory = new VerIDFactory(this);
    
    // Create face detection/recognition factory
    FaceDetectionRecognitionFactory faceDetectionRecognitionFactory = new FaceDetectionRecognitionFactory(this);
    
    // Create a set with the face template versions your app will use
    Set<VerIDFaceTemplateVersion> faceTemplateVersions = EnumSet.of(VerIDFaceTemplateVersion.V16, VerIDFaceTemplateVersion.V24);
    
    // Set the face template versions on the face detection/recognition factory
    faceDetectionRecognitionFactory.setFaceTemplateVersions(faceTemplateVersions);
    
    // Tell Ver-ID factory to use your face detection/recognition factory for face detection
    veridFactory.setFaceDetectionFactory(faceDetectionRecognitionFactory);
    
    // Tell Ver-ID factory to use your face detection/recognition factory for face recognition
    veridFactory.setFaceRecognitionFactory(faceDetectionRecognitionFactory);
    
    // Etc. (set Ver-ID factory delegate and create Ver-ID)
    ```

2. Optional\*: Copy the required resource files to the Ver-ID-Models folder inside your app's assets. Here is a table of the resources your app will need for different face template versions:

    | Always included |
    | --------------- |
    | fhogcascade\_face\_frontal.dat |
    | RFB-320.bin |
    | RFB-320.nv |
    | RFB-320.param |
    | shape\_predictor\_5\_face\_landmarks.dat |
    | shape\_predictor\_68\_face\_landmarks.dat |
    | facemask-20200720-dut23td+1700-251149%3000.nv |
    | SpoofDetectorModel\_1.0.7\_bst\_2022-11-26\_yl61\_opt.torchscript |
    | MoireDetectorModel\_ep100\_ntrn-627p-620n\_02\_res-98-99-96-0-5.tflite |
    
    #### Face template version specific files
    
    | File | Face template version |
    | ---- | --------------------- |
    | dlib\_face\_recognition\_resnet\_model\_v1.dat | V16 |
    | facenet-20170512.nv | V20 |
    | rec21-recfull-20211108vw-fn23i-s8597c-b144f6-c1062-t100m10-r14s3000-q06.nv | V21 |
    | mobilefacenet-opt.bin | V24 |
    | mobilefacenet-opt.param | V24 |
    | mobilefacenet-q12.nv | V24 |
    
    \* If you don't bundle the resource files with your app Ver-ID will download the pertinent files from a CDN.
    
## Ver-ID SDK artifacts hosted on Maven Central

- You can now **remove** `maven { url 'https://dev.ver-id.com/artifactory/gradle-release' }` from repositories in your build files.
- Ensure `mavenCentral()` is in your module's repositories.

--
</details>
<details>
<summary>What's new in Ver-ID 2.4</summary>

## Added a diagnostic method to compare given face templates
[`compareUserFaceTemplates`](https://appliedrecognition.github.io/Ver-ID-UI-Android/com/appliedrec/verid/core2/util/FaceTemplateDiagnostics.html#compareUserFaceTemplates(java.util.Map,com.appliedrec.verid.core2.util.ResultCallback)) accepts a map of users and their face templates. The advantage of this method over others in the class is that it doesn't require the face templates to be of registered users.
	
### Please note that version 2.4.0 has a bug where camera previews appear stretched on some devices. This bug has been fixed in 2.4.1.

--
</details>
<details>
<summary>What's new in Ver-ID 2.3</summary>

## Added face template diagnostic feature
You can now run population analysis on your registered user's faces. This may be useful if you're experiencing false acceptance. You can find how similar all faces are to each other and determine whether to raise or lower the authentication threshold.

1. Create an instance of [FaceTemplateDiagnostics](https://appliedrecognition.github.io/Ver-ID-UI-Android/com/appliedrec/verid/core2/util/FaceTemplateDiagnostics.html):
	
	```java
	VerID verID; // Obtained from VerIDFactory
	FaceTemplateDiagnostics diagnostics = new FaceTemplateDiagnostics(verID);
	```
2. Compare all registered faces:
	
	```java
	boolean hashUserNames = false; // Set to true to hash the user names in the output
	diagnostics.compareRegisteredFaceTemplates(hashUserNames, result -> {
		try {
			HashMap<VerIDFaceTemplateVersion,UserFaceComparison[]> comparisons = result.get();
			// Convert the result to JSON for submitting to further analysis
			String json = new Gson().toJson(comparisons);
			// Or find the most similar users among the faces
			for (VerIDFaceTemplateVersion faceTemplateVersion : VerIDFaceTemplateVersion.values()) {
				UserFaceComparison[] comparisonsWithSameVersion = comparisons.get(faceTemplateVersion);
				if (comparisonsWithSameVersion != null && comparisonsWithSameVersion.length > 0) {
					UserFaceComparison mostSimilarPair = diagnostics.findMostSimilarUsers(comparisonsWithSameVersion);
					// The most similar users
					System.out.println("Most similar users are "+mostSimilarPair.getFirst()+" and "+mostSimilarPair.getSecond());
				}
			}
		} catch (Exception e) {
			// TODO: Handle error
		}
	});
	```
	
--
</details>
<details>
<summary>What's new in Ver-ID 2.2</summary>

## Added the ability to load and use custom face attribute classifiers.
This features is helpful for extracting attributes from faces. For example, the SDK can now help you ascertain whether a face has a face covering or whether an image on an ID card is genuine.

### Example
Let's assume you have a machine learning model in a file called licence_classifier.nv in your application's files directory.
	
1. Load this model into Ver-ID:
	
	```java
	String licenceClassifierName = "licence";
	String licenceClassifierFileName = "licence_classifier.nv";
	String licenceClassifierFile = new File(context.getFilesDir(), licenceClassifierFileName);
	// Create an instance of the Classifier class
	Classifier licenceImageAuthenticityClassifier = new Classifier(licenceClassifierName, licenceClassifierFile.getPath());
	
	// Create VerIDFactory
	VerIDFactory veridFactory = new VerIDFactory(context);
	// Add the classifier
	((FaceDetectionRecognitionFactory)verIDFactory.getFaceDetectionFactory()).addClassifier(licenceImageAuthenticityClassifier);
	
	// You can check the classifiers you added using
	Set<Classifier> addedClassifiers = ((FaceDetectionRecognitionFactory)verIDFactory.getFaceDetectionFactory()).getAdditionalClassifiers();
	```
2. Extract face attributes using a classifier:
	
	```java
	// Create VerIDImage from a file
	VerIDImageBitmap image = getVerIDImageForAsset("path/to/your/image.jpg");
        IFaceDetection<FaceDetectionImage> faceDetection = getEnvironment().getFaceDetection();

	// Ensure face detection is an instance of FaceDetection supplied by Ver-ID
        if (!(faceDetection instanceof FaceDetection)) {
            throw new Exception("Unsupported implementation of IFaceDetection interface");
        }
	
	// Detect faces in image
        Face[] faces = faceDetection.detectFacesInImage(image.createFaceDetectionImage(), 1, 0);

	// Ensure face was detected
	if (faces.length == 0) {
		throw new Exception("Face not found");
	}
	
	// Extract face attribute using your classifier
        float score = ((FaceDetection) faceDetection).extractAttributeFromFace(faces[0], image, licenceClassifierName);
	```
	
--
</details>
<details>
<summary>What's new in Ver-ID 2.1</summary>

## Version 2.1 of the Ver-ID SDK introduces a new face template version.

### Background
Versions of Ver-ID prior to 2.1 generate face recognition templates version 16 (internal versioning). Version 2.1 introduces face recognition templates version 20. The new face templates significantly improve the accuracy of face recognition, especially for minority gender and race taxons.

Face templates version 20 are incompatible with version 16. The Ver-ID SDK offers a silent automatic migration to version 20 for its existing users. New installs of the SDK will automatically use version 20 face templates unless instructed otherwise.

### Migration choices
1. Let Ver-ID set the default face template version based on the existing registered faces. This is the default and in most cases the best choice. Ver-ID will check the existing registered faces and if no older (version 16) face templates are registered it will start generating face templates version 20. Otherwise it will set its default face template version to 20 and only register version 20 face templates. Once all users have both versions of the face templates the older version (16) faces can be deleted.
2. Set the face template version to the older version 16. New registrations will generate both versions of the face templates but version 16 will be used for face comparisons. Version 20 face templates will be ready should you decide to switch to version 20 later. Choose this option if you want the SDK to keep using version 16 face templates.
3. Set the face template version to 20. New registrations will also generate version 16 face templates but only if your database contains users with version 16 face templates. Otherwise only version 20 face templates will be generated and used for face comparison at authentication.

### Setting default face template version
If you choose to set the default face template version instead of relying on Ver-ID you can do so in the `FaceDetectionRecognitionFactory`:

```java
VerIDFaceTemplateVersion defaultFaceTemplateVersion = VerIDFaceTemplateVersion.V20; // Or VerIDFaceTemplateVersion.V16
VerIDFactory veridFactory = new VerIDFactory(context);
((FaceDetectionRecognitionFactory)verIDFactory.getFaceRecognitionFactory()).setDefaultFaceTemplateVersion(defaultFaceTemplateVersion);
```

### Automatically deleting face templates with older version
If you're using Ver-ID's implementation of the `IUserManagement` interface you can enable automatic face template migration in its factory. This will ensure that version 16 faces will be deleted when all users have version 20 faces registered. To do this set the call `setEnableAutomaticFaceTemplateMigration(true)` on an instance of `UserManagementFactory`.

```java
VerIDFactory veridFactory = new VerIDFactory(context);
((UserManagementFactory)verIDFactory.getUserManagementFactory()).setEnableAutomaticFaceTemplateMigration(true);
```

--
</details>
<details><summary>What's new in Ver-ID 2.0</summary>

## Version 2 of the Ver-ID SDK brings a number of improvements:

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

--
</details>

## Requirements

To build this project and to run the sample app you will need a computer with these applications:

- [Android Studio 4](https://developer.android.com/studio) with Gradle plugin version 4.0.0 or newer
- [Git](https://git-scm.com)
	
The SDK runs on Android 5.0 (API level 21) and newer.

## Running the sample app

1. Open a shell console and enter the following commands:

	~~~shell
	git clone https://github.com/AppliedRecognition/Ver-ID-UI-Android.git
	~~~
1. Open Android Studio and from the top menu select **File/Open...**. Navigate to the directory where you checked out the Git project and press **Open**.
1. Connect your Android device for debugging via USB.
1. In the main menu select **Run/Run 'sample'**. Select your Android device and press **OK**.

## Adding Ver-ID to your own project

1. Ensure `mavenCentral()` is in your project's repositories.
2. Add the following entries in your app module's **gradle.build** file:
    
    ~~~groovy
    dependencies {
        implementation 'com.appliedrec.verid:ui2:[2.8.3,3)'
    }
    ~~~
3. [Register your app](https://dev.ver-id.com/licensing/). You will need your app's package name.
4. Registering your app will generate an evaluation licence for your app. The licence is valid for 30 days. If you need a production licence please [contact Applied Recognition](mailto:sales@appliedrec.com).
5. When you finish the registration you'll receive a file called **Ver-ID identity.p12** and a password. Copy the password to a secure location.
6. Copy the **Ver-ID identity.p12** into your app's assets folder. A common location is **your\_app_module/src/main/assets**.
7. Ver-ID will need the password you received at registration.
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
