# Change Log

## Version 1.8
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
