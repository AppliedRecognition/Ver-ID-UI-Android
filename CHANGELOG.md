# Change Log

## Version 1.11.1
- Added face template encryption

## Version 1.11.0
- Added protected method `startTipsActivity()` on `VerIDSessionActivity`. Override the method if you want to present your own version of tips.

## Version 1.10.0
Ver-ID Core changes:
 
- Introduced a new constructor for `ResultEvaluationServiceFactory`. The new constructor doesn't need `Context`.
- Added `getFaceLandmarks()` method on `Face`

## Version 1.9.0
- Improvements of processing images for face detection and recognition.
	- The library now supports setting multiple image processors that each generate an image optimised for face detection.
	- When performing face detection the library tries each supplied image processor until it detects a face.
	- The default implementation has 2 image processors:
		1. One that converts the image to grayscale giving extra weight to the red channel.
		2. One that converts the image to grayscale as above plus brightens the image.
	- If the first image processor supplies an image that's too dark for the face detector to find a face the second image processor brightens the image and tries again.
	- If you have a particular way of converting images to grayscale you can use it instead of or in addition to Ver-ID's image processors. Create your own image processor and add it to an instance of `FaceDetectionRecognitionFactory` before creating `VerID` using `VerIDFactory`:

		~~~java
		// Example image processor service implementation
		IImageProcessorService myImageProcessor = new IImageProcessorService() {
			@Override
			public void prepareImageForFaceDetection(VerIDImage image, boolean recalculate) throws Exception {
				if (image.getGrayscale() != null && !recalculate) {
					return image.getGrayscale();
				}
				byte[] grayscale;
				// Process the image from YUV image or bitmap
				if (image.getYuvImage() != null) {
					grayscale = yuvToGrayscale(image.getYuvImage(), image.getYuvImageExifOrientation());
				} else if (image.getBitmap() != null) {
					grayscale = bitmapToGrayscale(image.getBitmap(), image.getBitmapExifOrientation());
				} else {
					throw new Exception("Unsupported image type");
				}
				image.setGrayscale(grayscale);
				image.setGrayscaleProcessorName(getName());
			}
			
			@Override
			public String getName() {
				// Pick a unique name – used to see if the image has already been processed with the same image processor
				return "03.MyImageProcessor";
			}
		};
		
		Context context; // Set this to a context your application or activity
		VerIDFactoryDelegate delegate; // Set this to your implementation of VerIDFactoryDelegate
		FaceDetectionRecognitionFactory faceDetectionRecognitionFactory = new FaceDetectionRecognitionFactory(context);
		// Get the default image processors
		IImageProcessorService[] imageProcessors = faceDetectionRecognitionFactory.getImageProcessors();
		// Add your own image processor
		System.arraycopy(new IImageProcessorService[]{myImageProcessor}, 0, imageProcessors, imageProcessors.length, 1);
		// Set the image processors on the factory
		faceDetectionRecognitionFactory.setImageProcessors(imageProcessors);
		
		VerIDFactory veridFactory = new VerIDFactory(context, delegate);
		veridFactory.setFaceDetectionFactory(faceDetectionRecognitionFactory);
		veridFactory.setFaceRecognitionFactory(faceDetectionRecognitionFactory);
		verid.createVerID();
		~~~

## Version 1.8.0
- Added the option to modify the face extract quality threshold and landmark tracking quality threshold at run time.
	- **Face extract quality threshold:** Face template extraction (for face recognition) will only be run if the detected face's quality meets this threshold. Lower the threshold to accept faces with poorer quality for face recognition.

		~~~java
		VerID verid; // VerID instance obtained from VerIDFactory
		IFaceDetection faceDetection = verid.getFaceDetection();
		if (faceDetection instanceof FaceDetection) {
			((FaceDetection)faceDetection).setFaceExtractQualityThreshold(7.5f);
		}
		~~~
	- **Landmark tracking quality threshold:** Full face detection (slower) will be run if the detected face quality falls below this threshold when tracking a face in a sequence of images. Lower the threshold to reduce the frequency of full face detection at the expense of face quality.

		~~~java
		VerID verid; // VerID instance obtained from VerIDFactory
		IFaceDetection faceDetection = verid.getFaceDetection();
		if (faceDetection instanceof FaceDetection) {
			((FaceDetection)faceDetection).setLandmarkTrackingQualityThreshold(5.5f);
		}
		~~~

## Version 1.7.11
- We found that apps targeting API level 18+ didn't comply with Android 64-bit requirement due to RenderScript bitcode. The solution is to target API level 21+ separately from the 14–20.
  - Use `implementation 'com.appliedrec.verid:ui:1.7.11'` if you are targeting Android API level 21+.
  - Use `implementation 'com.appliedrec.verid:ui-api14:1.7.11'` if you are targeting Android API level 14–20.

## Version 1.7.8
- Separate libraries for Android API level 14–17 and 18+
  - Use `implementation 'com.appliedrec.verid:ui:1.7.8'` if you are targeting Android API level 18+.
  - Use `implementation 'com.appliedrec.verid:ui-api14:1.7.8'` if you are targeting Android API level 14–17.
- SDK ready for 64-bit architectures

## Version 1.7.4
VerIDModels are now packaged in the Ver-ID Core dependency. Please delete the VerIDModels folder from your app's assets folder to avoid conflicts.

## Version 1.1.0
### Dynamic translations
You can now supply your own language translation to Ver-ID sessions at run time. Details can be found [here](./Translating-Ver-ID-UI.md).
