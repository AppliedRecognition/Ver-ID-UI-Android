# Change Log
## Version 1.20.0    
- Added an option to use legacy Android camera API in Ver-ID sessions, although on most devices the newer camera API performs better. Call `shouldUseLegacyCameraAPI(true)` on `VerIDSessionSettings` to enable the older camera API in session fragments. This feature is only available on targets built for Android API level 21 or newer. Older targets use the legacy API by default.

## Version 1.19.0
- Added a new method to pass Ver-ID SDK identity to *VerIDFactory*.

    The library now depends on [Ver-ID SDK Identity](https://github.com/AppliedRecognition/Ver-ID-SDK-Identity-Android). You can construct your app's Ver-ID SDK identity in a variety of ways before passing it to *VerIDFactory*. It's more transparent (you can check out the source of the Ver-ID SDK Identity project) and more flexible than entering your credentials directly into the Ver-ID SDK.

## Version 1.18.0
- Added a `cleanup()` method on `VerID` to remove temporary files generated by Ver-ID sessions.

## Version 1.17.0
- Fixed a bug that would prevent video from being recorded in sessions that didn't display the result.
- Deprecated video encoder service.

## Version 1.16.2
- Fixed a bug which caused a crash when camera was started before the session fragment view was loaded

## Version 1.16.1
- Lint cleanup and code optimization

## Version 1.16.0
- Added new licensing system
- Added new constructors in *VerIDFactory*

## Version 1.15.0
- Made `chooseOptimalSize(Size[] choices, int width, int height)` in *VerIDSessionFragment* a protected instance method.

## Version 1.14.4
- Fixes in preview orientation and scaling on devices with non-standard camera sensor orientation.

## Version 1.14.3
- Using Android `camera2` API in Ver-ID session fragment.

## Version 1.14.0
- Migrated to AndroidX. If your app uses Android support libraries please [migrate to AndroidX](https://developer.android.com/jetpack/androidx/migrate) to use Ver-ID 1.14.0.
- Removed dependency on RenderScript. You can still [enable RenderScript](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.FaceDetectionRecognitionSettings.html#setEnableRenderScript(boolean)) in `com.appliedrec.verid:ui:1.14.0` but `com.appliedrec.verid:ui-api14:1.14.0` will ignore the setting.

## Version 1.13.1
- Fixed session not restarting after dismissing the session failure dialog using the system back button.

## Version 1.13.0
- Added a [blocking method](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.VerIDFactory.html#createVerIDSync()) to create `VerID` instance.

## Version 1.12.0
- Fixed error where sessions returned encrypted face templates.
- Added the option to [disable face template encryption](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.UserManagementFactory.html#UserManagementFactory(Context,%20boolean)).
- Added a [method](https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.UserManagement.html#getFaceTemplateEncryption()) to access face template encryption.

## Version 1.11.2
- Animations showing how to follow the arrow directions take into account the requested direction. For example, if the user is asked to turn left and fails the animation will show a head following the arrow to the left.

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
