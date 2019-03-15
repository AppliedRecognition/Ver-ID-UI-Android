![Maven metadata URL](https://img.shields.io/maven-metadata/v/https/dev.ver-id.com/artifactory/gradle-release/com/appliedrec/verid/ui/maven-metadata.xml.svg)

# Ver-ID UI for Android

## Installation
1. Add the Applied Recognition repository to the repositories in your app module's **gradle.build** file:
    
    ~~~groovy
    repositories {
        maven {
            url 'https://dev.ver-id.com/artifactory/gradle-release'
        }
    }
    ~~~
1. Add the following dependency to your **gradle.build** file:
	
	~~~groovy
    dependencies {
	    implementation 'com.appliedrec.verid:ui:1.0.0-beta.3'
    }
	~~~
2. Add RenderScript in your **gradle.build** file:

	~~~groovy
    android {
        defaultConfig {
            renderscriptTargetApi 14
            renderscriptSupportModeEnabled true
        }
    }
	~~~
3. Clone [Ver-ID-Models](https://github.com/AppliedRecognition/Ver-ID-Models/tree/matrix-16) and add the contents as a folder named **VerIDModels** to your app's **assets** folder.
4. [Request API secret](https://dev.ver-id.com/admin/register) for your app.
5. Add the API secret in your app's manifest XML:

	~~~xml
    <manifest>
        <application>
            <meta-data
                android:name="com.appliedrec.verid.apiSecret"
                android:value="yourApiSecret" />
        </application>
    </manifest>
	~~~

## Usage

### Creating Ver-ID Environment
Prior to running Ver-ID sessions you will need to create an instance of Ver-ID.

~~~java
VerIDFactory verIDFactory = new VerIDFactory(getContext(), new VerIDFactoryDelegate() {
    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
        // You can now use the VerID instance
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
        // Failed to create an instance of Ver-ID
    }
});
verIDFactory.createVerID();
~~~

### Running Ver-ID Session Activities
~~~java
class MyActivity extends AppCompatActivity {

    static final int REQUEST_CODE_LIVENESS_DETECTION = 0;

    void startLivenessDetectionSession() {
        VerIDFactory veridFactory = new VerIDFactory(this, new VerIDFactoryDelegate() {
            @Override
            public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
                // You can now start a Ver-ID session
                LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
                settings.setNumberOfResultsToCollect(2);
                Intent intent = new VerIDSessionIntent(this, verID, settings);
                startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
            }

            @Override
            public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
                // Failed to create an instance of Ver-ID
            }
        });
        veridFactory.createVerID();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION && resultCode == RESULT_OK && data != null) {
            SessionResult sessionResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (sessionResult != null && sessionResult.getError() == null) {
                // Liveness detection session succeeded
            }
        }
    }
}
~~~

### Customizing Ver-ID Session Behaviour
The Ver-ID session activity finishes as the session concludes. If you want to change this or other behaviour of the session you can write your own activity class that extends `VerIDSessionActivity`.

For an example of an activity that extends `VerIDSessionActivity` take a look at [AuthenticationActivity](https://github.com/AppliedRecognition/Ver-ID-Kiosk-Android/blob/master/app/src/main/java/com/appliedrec/verid/kiosk/AuthenticationActivity.java) in the [Ver-ID Kiosk sample app](https://github.com/AppliedRecognition/Ver-ID-Kiosk-Android).

### Controlling Liveness Detection
If you run a Ver-ID session with `LivenessDetectionSessionSettings` or its subclass `AuthenticationSessionSettings` you can control how Ver-ID detects liveness.

To disable liveness detection set the session's [number of results to collect](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.SessionSettings.html#setNumberOfResultsToCollect(int)) to `1`.

To control the bearings the user may be asked to assume call the [setBearings(EnumSet)](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.LivenessDetectionSessionSettings.html#setBearings(EnumSet)) method. For example, to ask the user to look straight at the camera and then assume 2 random poses from the choice of left and right modify the settings as follows:

~~~java
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
settings.setNumberOfResultsToCollect(3); // 1 straight plus 2 other poses
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
