# Migrating from Ver-ID 1 to Ver-ID 2

## Loading Ver-ID

### Ver-ID 1
```java
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
```

### Ver-ID 2
```java
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
```
Alternatively, you can use the new reactive interface:

```java
VerIDFactory verIDFactory = new VerIDFactory(getContext());
Disposable veridFactoryDisposable = veridFactory
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        verid -> {
            // You can now use the VerID instance
        },
        error -> {
            // Failed to create an instance of Ver-ID
        }
    );
```

## Running Ver-ID sessions

### Ver-ID 1
```java
class MyActivity extends AppCompatActivity {

    static final int REQUEST_CODE_LIVENESS_DETECTION = 0;
    VerID verID;

    void startLivenessDetectionSession() {
        // Start a Ver-ID session
        LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
        Intent intent = new VerIDSessionIntent(this, verID, settings);
        startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION && resultCode == RESULT_OK && data != null) {
            VerIDSessionResult sessionResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (sessionResult != null && sessionResult.getError() == null) {
                // Liveness detection session succeeded
            }
        }
    }
}
```

### Ver-ID 2
```java
class MyActivity extends AppCompatActivity implements VerIDSessionDelegate {

    VerID verID;

    void startLivenessDetectionSession() {
        // Start a Ver-ID session
        LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
        VerIDSession session = new VerIDSession(verID, settings);
        session.setDelegate(this);
        session.start();
    }
    
    @Override
    public void onSessionFinished(IVerIDSession<?> session, VerIDSessionResult result) {
        if (!result.getError().isPresent()) {
            // Liveness detection session succeeded
        }
    }
}
```

## Extracting captured face images from session results

### Ver-ID 1
```java
Iterator<Map.Entry<Face, Uri>> faceImageIterator = sessionResult.getFaceImages(Bearing.STRAIGHT).entrySet().iterator();
ArrayList<Bitmap> faceImages = new ArrayList<>();
while (faceImageIterator.hasNext()) {
    Map.Entry<Face, Uri> entry = faceImageIterator.next();
    Uri imageUri = entry.getValue();
    try (InputStream inputStream = getContext().getContentResolver().openInputStream(imageUri)) {
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        if (bitmap != null) {
            Rect faceBounds = new Rect();
            entry.getKey().getBounds().round(faceBounds);
            faceBounds.left = Math.max(faceBounds.left, 0);
            faceBounds.top = Math.max(faceBounds.top, 0);
            faceBounds.right = Math.min(faceBounds.right, bitmap.getWidth());
            faceBounds.bottom = Math.min(faceBounds.bottom, bitmap.getHeight());
            bitmap = Bitmap.createBitmap(bitmap, faceBounds.left, faceBounds.top, faceBounds.width(), faceBounds.height());
            faceImages.add(bitmap);
        }
    } catch (Exception ignore) {
    }
}

```

### Ver-ID 2
```java
ArrayList<Bitmap> faceImages = new ArrayList<>();
for (FaceCapture faceCapture : sessionResult.getFaceCaptures()) {
    faceImages.add(faceCapture.getFaceImage());
}
```