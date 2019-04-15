# Migrating from legacy Ver-ID SDK

The Ver-ID SDK has been split into 2 separate libraries: Ver-ID Core and Ver-ID UI. 

### Ver-ID Core
The proprietary Ver-ID Core library runs face detection and face recognition. It also contains liveness detection logic.

### Ver-ID UI
The open source Ver-ID UI library handles user interaction and provides a graphical user interface.

## Loading/unloading Ver-ID

### Legacy
In the legacy Ver-ID SDK your application had to call the `load` method on the `VerID` shared instance singleton to load the library. It later had to call the `unload` method on the instance to free the library resources.

### New
The new SDK doesn't use the `VerID` singleton. Instead your application must use `VerIDFactory` to create a `VerID` instance. This instance is then used for face detection, face recognition and for storing user's faces.

The `load` and `unload` methods have been removed.

To load Ver-ID:

~~~java
Context context = this; // If you're in an activity
VerIDFactory veridFactory = new VerIDFactory(this, new VerIDFactoryDelegate() {
	@Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory factory, VerID environment) {
        // environment is your VerID instance
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory factory, Exception error) {
        // Inspect the exception to find out what went wrong
    }
});
veridFactory.createVerID();
~~~

## Running Ver-ID sessions

### Legacy

~~~java
VerIDLivenessDetectionSessionSettings settings = new VerIDLivenessDetectionSessionSettings();
VerIDSessionIntent intent = new VerIDSessionIntent(this, settings);
startActivityForResult(intent);
~~~

### New

~~~java
VerID environment; // Obtained from VerIDFactory see previous section
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
VerIDSessionIntent intent = new VerIDSessionIntent(this, environment, settings);
startActivityForResult(intent);
~~~

## Obtaining session result

### Legacy

~~~java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && data != null) {
        VerIDSessionResult result = data.getParcelableExtra(VerIDActivity.EXTRA_SESSION_RESULT);      
    }
}
~~~

### New

~~~java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && data != null) {
        VerIDSessionResult result = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);        
    }
}
~~~