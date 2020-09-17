package com.appliedrec.verid.ui2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

/**
 * The type Abstract session activity.
 *
 * @param <SessionFragment> the type parameter
 */
public abstract class AbstractSessionActivity<SessionFragment extends AbstractSessionFragment<?>> extends AppCompatActivity implements ISessionActivity, Iterable<FaceBounds>, Iterator<FaceBounds> {

    /**
     * The constant EXTRA_SESSION_ID.
     */
    public static final String EXTRA_SESSION_ID = "com.appliedrec.verid.EXTRA_SESSION_ID";
    /**
     * The constant REQUEST_CODE_CAMERA_PERMISSION.
     */
    protected static final int REQUEST_CODE_CAMERA_PERMISSION = 10;
    private VerIDSessionSettings sessionSettings;
    private CameraLocation cameraLocation;
    private final VerIDImageAnalyzer imageAnalyzer = new VerIDImageAnalyzer(this);
    private ExecutorService backgroundExecutor;
    private final ArrayList<Bitmap> faceImages = new ArrayList<>();
    private final SynchronousQueue<Size> viewFinderSizeQueue = new SynchronousQueue<>();
    private final View.OnLayoutChangeListener onLayoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        Size newSize = new Size(right-left, bottom-top);
        if (newSize.width > 0 && newSize.height > 0) {
            executeInBackground(() -> {
                try {
                    viewFinderSizeQueue.put(newSize);
                } catch (InterruptedException ignore) {
                }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        faceImages.clear();
        backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSessionFragment().flatMap(sessionFragment -> Optional.ofNullable(sessionFragment.getView())).ifPresent(view -> {
            Size newSize = new Size(view.getWidth(), view.getHeight());
            if (newSize.width > 0 && newSize.height > 0) {
                executeInBackground(() -> {
                    try {
                        viewFinderSizeQueue.put(newSize);
                    } catch (InterruptedException ignore) {
                    }
                });
            }
            view.addOnLayoutChangeListener(onLayoutChangeListener);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSessionFragment().flatMap(sessionFragment -> Optional.ofNullable(sessionFragment.getView())).ifPresent(view -> {
            view.removeOnLayoutChangeListener(onLayoutChangeListener);
        });
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        backgroundExecutor = null;
        faceImages.clear();
    }

    //region ISessionActivity

    @Override
    public void setSessionSettings(VerIDSessionSettings settings, CameraLocation cameraLocation) {
        this.sessionSettings = settings;
        this.cameraLocation = cameraLocation;
    }

    @Override
    public void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt) {
        runOnUiThread(() -> {
            getSessionFragment().ifPresent(fragment -> {
                fragment.accept(faceDetectionResult, prompt);
            });
        });
    }

    @Override
    public void setVerID(VerID verID) {
        this.imageAnalyzer.setVerID(verID);
    }

    @Override
    public IImageFlowable getImageFlowable() {
        return getImageAnalyzer();
    }

    @Override
    public void accept(FaceCapture faceCapture) {
        if (sessionSettings instanceof RegistrationSessionSettings) {
            runOnUiThread(() -> {
                float screenDensity = getResources().getDisplayMetrics().density;
                executeInBackground(() -> {
                    float targetHeightDp = 96f;
                    float scale = targetHeightDp / (float) faceCapture.getFaceImage().getHeight() * screenDensity;
                    Bitmap bitmap = Bitmap.createScaledBitmap(faceCapture.getFaceImage(), Math.round((float) faceCapture.getFaceImage().getWidth() * scale), Math.round((float) faceCapture.getFaceImage().getHeight() * scale), true);
                    if (cameraLocation == CameraLocation.FRONT) {
                        Matrix matrix = new Matrix();
                        matrix.setScale(-1, 1);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    }
                    faceImages.add(bitmap);
                    runOnUiThread(this::drawFaces);
                });
            });
        }
    }

    //endregion

    /**
     * Gets exif orientation.
     *
     * @param rotationDegrees the rotation degrees
     * @return the exif orientation
     */
    protected final @VerIDImageAnalyzer.ExifOrientation int getExifOrientation(int rotationDegrees) {
        int exifOrientation;
        switch (rotationDegrees) {
            case 90:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                break;
            case 180:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                break;
            case 270:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                break;
            default:
                exifOrientation = ExifInterface.ORIENTATION_NORMAL;
        }
        if (cameraLocation == CameraLocation.FRONT) {
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSVERSE;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSPOSE;
                    break;
            }
        }
        return exifOrientation;
    }

    /**
     * Execute in background.
     *
     * @param runnable the runnable
     */
    protected final void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }

    /**
     * Gets image analyzer.
     *
     * @return the image analyzer
     */
    protected final VerIDImageAnalyzer getImageAnalyzer() {
        return imageAnalyzer;
    }

    /**
     * Gets camera location.
     *
     * @return the camera location
     */
    protected final CameraLocation getCameraLocation() {
        return cameraLocation;
    }

    /**
     * Gets default face extents.
     *
     * @return the default face extents
     */
    protected final FaceExtents getDefaultFaceExtents() {
        return sessionSettings.getExpectedFaceExtents();
    }

    //region Abstract methods

    /**
     * Gets session fragment.
     *
     * @return the session fragment
     */
    protected abstract Optional<SessionFragment> getSessionFragment();

    /**
     * Gets face images view.
     *
     * @return the face images view
     */
    protected abstract Optional<LinearLayout> getFaceImagesView();

    /**
     * Start camera.
     */
    protected abstract void startCamera();

    //endregion

    //region Iterable<FaceBounds>

    @NonNull
    @Override
    public Iterator<FaceBounds> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public FaceBounds next() {
        try {
            return new FaceBounds(viewFinderSizeQueue.take(), getDefaultFaceExtents());
        } catch (InterruptedException e) {
            e.printStackTrace();
            return new FaceBounds(Size.ZERO, getDefaultFaceExtents());
        }
    }

    //endregion

    //region Drawing face captures

    @DrawableRes
    private int placeholderImageForBearing(Bearing bearing) {
        switch (bearing) {
            case STRAIGHT:
                return R.mipmap.head_straight;
            case LEFT:
                return R.mipmap.head_left;
            case RIGHT:
                return R.mipmap.head_right;
            case UP:
                return R.mipmap.head_up;
            case DOWN:
                return R.mipmap.head_down;
            case LEFT_UP:
                return R.mipmap.head_left_up;
            case RIGHT_UP:
                return R.mipmap.head_right_up;
            case LEFT_DOWN:
                return R.mipmap.head_left_down;
            case RIGHT_DOWN:
                return R.mipmap.head_right_down;
            default:
                return R.mipmap.head_straight;
        }
    }

    /**
     * Draw faces.
     */
    @MainThread
    protected void drawFaces() {
        getFaceImagesView().ifPresent(faceImagesView -> {
            faceImagesView.removeAllViews();
            if (sessionSettings instanceof RegistrationSessionSettings) {
                float screenDensity = getResources().getDisplayMetrics().density;
                int height = Math.round(screenDensity * 96f);
                int margin = Math.round(screenDensity * 8f);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
                Bearing[] bearingsToRegister = ((RegistrationSessionSettings) sessionSettings).getBearingsToRegister();
                executeInBackground(() -> {
                    ArrayList<RoundedBitmapDrawable> bitmapDrawables = new ArrayList<>();
                    for (int i = 0; i<sessionSettings.getFaceCaptureCount(); i++) {
                        RoundedBitmapDrawable drawable;
                        if (i < faceImages.size()) {
                            Bitmap bitmap = faceImages.get(i);
                            drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                        } else {
                            int bearingIndex = i % bearingsToRegister.length;
                            Bearing bearing = bearingsToRegister[bearingIndex];
                            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), placeholderImageForBearing(bearing));
                            if (cameraLocation == CameraLocation.FRONT) {
                                Matrix matrix = new Matrix();
                                matrix.setScale(-1, 1);
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                            }
                            drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                        }
                        drawable.setCornerRadius(height / 6f);
                        bitmapDrawables.add(drawable);
                    }
                    runOnUiThread(() -> {
                        getFaceImagesView().ifPresent(faceImagesView2 -> {
                            for (RoundedBitmapDrawable drawable : bitmapDrawables) {
                                ImageView imageView = new ImageView(this);
                                imageView.setScaleType(ImageView.ScaleType.CENTER);
                                imageView.setImageDrawable(drawable);
                                layoutParams.leftMargin = margin;
                                layoutParams.rightMargin = margin;
                                faceImagesView2.addView(imageView, layoutParams);
                            }
                        });
                    });
                });
            }
        });
    }

    //endregion

    //region Camera permissions

    @Override
    public final void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            for (int i=0; i<permissions.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        startCamera();
                    } else {
                        imageAnalyzer.fail(new VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED));
                    }
                    return;
                }
            }
        }
    }

    /**
     * Has camera permission boolean.
     *
     * @return the boolean
     */
    protected final boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    //endregion
}
