package com.appliedrec.verid.sample;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.FaceRecognition;
import com.appliedrec.verid.core2.IRecognizable;
import com.appliedrec.verid.core2.IdentifiedFace;
import com.appliedrec.verid.core2.UserIdentification;
import com.appliedrec.verid.core2.UserIdentificationCallbacks;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.VerIDFaceTemplateVersion;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityIdentificationDemoBinding;
import com.appliedrec.verid.ui2.IVerIDSession;
import com.appliedrec.verid.ui2.VerIDSession;
import com.appliedrec.verid.ui2.VerIDSessionDelegate;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.disposables.Disposable;

public class IdentificationDemoActivity extends AppCompatActivity implements IVerIDLoadObserver, VerIDSessionDelegate {

    private ActivityIdentificationDemoBinding viewBinding;
    private VerID verID;
    static final int MAX_FACES = 1000000;
    private IRecognizable[] generatedFaces = new IRecognizable[0];
    private ArrayList<Disposable> disposables = new ArrayList<>();
    private AtomicBoolean isSessionRunning = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityIdentificationDemoBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewBinding.facesTextField.setImeOptions(EditorInfo.IME_ACTION_GO);
        viewBinding.facesTextField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_GO) {
                generateFaces();
                return true;
            }
            return false;
        });
        showKeyboard();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        Iterator<Disposable> disposableIterator = disposables.iterator();
        while (disposableIterator.hasNext()) {
            Disposable disposable = disposableIterator.next();
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
            disposableIterator.remove();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.identification_demo, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.start_demo).setEnabled(verID != null && (verID.getFaceRecognition() instanceof FaceRecognition) && !isSessionRunning.get());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.start_demo) {
            generateFaces();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        invalidateOptionsMenu();
    }

    @Override
    public void onVerIDUnloaded() {
        this.verID = null;
    }

    private void generateFaces() {
        View focusedView = getCurrentFocus();
        if (focusedView != null) {
            InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
            }
        }
        int facesToGenerate = Integer.parseInt(viewBinding.facesTextField.getText().toString());
        if (facesToGenerate > MAX_FACES || facesToGenerate < 1) {
            Toast.makeText(this, "Please enter a number between 1 and "+MAX_FACES, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isSessionRunning.compareAndSet(false, true)) {
            return;
        }
        invalidateOptionsMenu();
        viewBinding.progressBar.setMax(facesToGenerate);
        viewBinding.progressBar.setProgress(0);
        viewBinding.progressBar.setVisibility(View.VISIBLE);
        viewBinding.facesTextField.setVisibility(View.INVISIBLE);
        viewBinding.label.setText("Generating faces");
        Disposable userFacesDisposable = verID.getUserManagement().getFacesOfUserSingle(VerIDUser.DEFAULT_USER_ID).subscribe(userFaces -> {
            FaceRecognition faceRecognition = (FaceRecognition)verID.getFaceRecognition();
            int processorCount = Runtime.getRuntime().availableProcessors();
            int facesPerTask = facesToGenerate / processorCount;
            int remainder = facesToGenerate % processorCount;
            int reportingIndex = (int)Math.ceil((float)facesToGenerate / 100f);
            generatedFaces = new IRecognizable[facesToGenerate+userFaces.length];
            final VerIDFaceTemplateVersion defaultVersion;
            if (verID.getFaceRecognition() instanceof FaceRecognition) {
                defaultVersion = ((FaceRecognition)verID.getFaceRecognition()).defaultFaceTemplateVersion;
            } else {
                defaultVersion = VerIDFaceTemplateVersion.V16;
            }
            Function<IRecognizable,VerIDFaceTemplateVersion> templateVersionFromFace = face -> {
                try {
                    return VerIDFaceTemplateVersion.fromSerialNumber(face.getVersion());
                } catch (VerIDCoreException ignore) {
                    return null;
                }
            };
            VerIDFaceTemplateVersion version = null;
            FluentIterable<IRecognizable> userFacesIterable = FluentIterable.from(userFaces);
            FluentIterable<VerIDFaceTemplateVersion> userFaceVersions = userFacesIterable.transform(templateVersionFromFace).filter(Objects::nonNull);
            if (userFaceVersions.contains(defaultVersion)) {
                version = defaultVersion;
            } else {
                for (VerIDFaceTemplateVersion v : FluentIterable.from(VerIDFaceTemplateVersion.values()).filter(v -> v != defaultVersion).toSortedSet((a,b) -> b.getValue() - a.getValue())) {
                    if (userFaceVersions.contains(v)) {
                        version = v;
                        break;
                    }
                }
            }
            final VerIDFaceTemplateVersion targetVersion = version;
            userFaces = userFacesIterable.filter(face -> {
                try {
                    return VerIDFaceTemplateVersion.fromSerialNumber(face.getVersion()) == targetVersion;
                } catch (VerIDCoreException e) {
                    return false;
                }
            }).toArray(IRecognizable.class);
            System.arraycopy(userFaces, 0, generatedFaces, facesToGenerate, userFaces.length);
            AtomicInteger tasksExecuted = new AtomicInteger(0);
            AtomicInteger faceCounter = new AtomicInteger(0);
            int startIndex = 0;
            for (int i=0; i<processorCount; i++) {
                int size = facesPerTask + remainder;
                remainder = 0;
                GenerateFaces task = new GenerateFaces(size, faceCounter, startIndex, reportingIndex, version, processed -> {
                    if (viewBinding == null) {
                        return;
                    }
                    viewBinding.progressBar.setProgress(processed);
                    viewBinding.label.setText(String.format("Generated %d faces of %d", processed, facesToGenerate));
                }, (faces, idx) -> {
                    if (viewBinding == null) {
                        return;
                    }
                    System.arraycopy(faces, 0, generatedFaces, idx, faces.length);
                    if (tasksExecuted.incrementAndGet() == processorCount) {
                        viewBinding.progressBar.setVisibility(View.GONE);
                        viewBinding.label.setText("Faces to generate");
                        viewBinding.facesTextField.setVisibility(View.VISIBLE);
                        startSession();
                    }
                }, error -> {
                    if (viewBinding == null) {
                        return;
                    }
                    viewBinding.progressBar.setVisibility(View.GONE);
                    viewBinding.label.setText("Faces to generate");
                    viewBinding.facesTextField.setVisibility(View.VISIBLE);
                });
                startIndex += size;
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, faceRecognition);
            }
        }, error -> {
            if (viewBinding == null) {
                return;
            }
            viewBinding.progressBar.setVisibility(View.GONE);
            viewBinding.label.setText("Faces to generate");
            viewBinding.facesTextField.setVisibility(View.VISIBLE);
            showAlert("Failed to generate faces");
        });
        disposables.add(userFacesDisposable);
    }

    private void startSession() {
        LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
        sessionSettings.setFaceCaptureCount(1);
        VerIDSession session = new VerIDSession(verID, sessionSettings);
        session.setDelegate(this);
        session.start();
    }

    @Override
    public void onSessionFinished(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        result.getFirstFaceCapture(Bearing.STRAIGHT).ifPresent(faceCapture -> {
            UserIdentification userIdentification = new UserIdentification(verID);
            IRecognizable face = faceCapture.getFace();
            double faceCount = generatedFaces.length;
            if (viewBinding == null) {
                return;
            }
            viewBinding.progressBar.setMax((int)faceCount);
            viewBinding.label.setText(String.format("Identifying you in %d faces", (int)faceCount));
            userIdentification.findFacesSimilarTo(face, generatedFaces, null, new UserIdentificationCallbacks() {
                @Override
                public void onProgress(double progress) {
                    if (viewBinding == null) {
                        return;
                    }
                    int itemCount = (int)(progress * faceCount);
                    viewBinding.label.setText(String.format("Identifying you in face %d of %d", itemCount, (int)faceCount));
                    viewBinding.progressBar.setVisibility(View.VISIBLE);
                    viewBinding.progressBar.setProgress(itemCount);
                }

                @Override
                public void onError(Throwable error) {
                    if (viewBinding == null) {
                        return;
                    }
                    if (isSessionRunning.getAndSet(false)) {
                        invalidateOptionsMenu();
                        viewBinding.label.setText("Faces to generate");
                        viewBinding.progressBar.setVisibility(View.GONE);
                        new AlertDialog.Builder(IdentificationDemoActivity.this)
                                .setMessage("Face comparison failed")
                                .setNeutralButton("OK", null)
                                .create()
                                .show();
                    }
                }

                @Override
                public void onComplete(IdentifiedFace[] identifiedFaces) {
                    if (viewBinding == null) {
                        return;
                    }
                    if (isSessionRunning.getAndSet(false)) {
                        invalidateOptionsMenu();
                        viewBinding.label.setText("Faces to generate");
                        viewBinding.progressBar.setVisibility(View.GONE);
                        if (identifiedFaces.length == 0) {
                            showAlert("We were unable to identify you");
                        } else {
                            Arrays.sort(identifiedFaces, (lhs, rhs) -> {
                                if (lhs.getScore() == rhs.getScore()) {
                                    return 0;
                                }
                                return lhs.getScore() > rhs.getScore() ? -1 : 1;
                            });
                            IRecognizable bestFace = identifiedFaces[0].getFace();
                            Disposable disposable = verID.getUserManagement().getUserInFaceSingle(bestFace).subscribe(user -> {
                                if (VerIDUser.DEFAULT_USER_ID.equals(user)) {
                                    RoundedBitmapDrawable drawable = new ProfilePhotoHelper(IdentificationDemoActivity.this).getProfilePhotoDrawable((int)(100f * getResources().getDisplayMetrics().density));
                                    ImageView imageView = new ImageView(IdentificationDemoActivity.this);
                                    imageView.setImageDrawable(drawable);
                                    AlertDialog alertDialog = new AlertDialog.Builder(IdentificationDemoActivity.this)
                                            .setView(imageView)
                                            .setMessage(String.format("You've been identified among %.0f faces", faceCount))
                                            .setNeutralButton("OK", (dialogInterface, i) -> {
                                                dialogInterface.dismiss();
                                            })
                                            .create();
                                    alertDialog.setOnDismissListener(dialogInterface -> {
                                        showKeyboard();
                                    });
                                    alertDialog.show();
                                } else {
                                    showAlert("You have been misidentified");
                                }
                            }, error -> {
                                showAlert("Identification failed");
                            });
                            disposables.add(disposable);
                        }
                    }
                }
            });
        });
    }

    private void showAlert(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(IdentificationDemoActivity.this)
                .setMessage(message)
                .setNeutralButton("OK", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                })
                .create();
        alertDialog.setOnDismissListener(dialogInterface -> {
            showKeyboard();
        });
        alertDialog.show();
    }

    private void showKeyboard() {
        if (viewBinding == null) {
            return;
        }
        viewBinding.facesTextField.requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(viewBinding.facesTextField, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onSessionCanceled(@NonNull IVerIDSession<?> session) {
        isSessionRunning.set(false);
        invalidateOptionsMenu();
        if (viewBinding == null) {
            return;
        }
        viewBinding.label.setText("Faces to generate");
        viewBinding.progressBar.setVisibility(View.GONE);
        viewBinding.facesTextField.setVisibility(View.VISIBLE);
        showKeyboard();
    }

    private static class GenerateFaces extends AsyncTask<FaceRecognition, Integer, IRecognizable[]> {

        int numberOfFaces;
        AtomicInteger counter;
        int startIndex;
        int reportAtEach;
        VerIDFaceTemplateVersion faceTemplateVersion;
        ProgressCallback progressCallback;
        GenerateFacesCallback callback;
        ErrorCallback errorCallback;
        private AtomicReference<VerIDCoreException> errorRef = new AtomicReference<>();

        GenerateFaces(int numberOfFaces, AtomicInteger counter, int startIndex, int reportAtEach, VerIDFaceTemplateVersion faceTemplateVersion, ProgressCallback progressCallback, GenerateFacesCallback callback, ErrorCallback errorCallback) {
            this.numberOfFaces = numberOfFaces;
            this.counter = counter;
            this.startIndex = startIndex;
            this.reportAtEach = reportAtEach;
            this.faceTemplateVersion = faceTemplateVersion;
            this.progressCallback = progressCallback;
            this.callback = callback;
            this.errorCallback = errorCallback;
        }

        @Override
        protected IRecognizable[] doInBackground(FaceRecognition... faceRecognitions) {
            FaceRecognition faceRecognition = faceRecognitions[0];
            IRecognizable[] faces = new IRecognizable[numberOfFaces];
            for (int i=0; i<numberOfFaces; i++) {
                try {
                    faces[i] = faceRecognition.generateRandomFaceTemplate(this.faceTemplateVersion);
                    int count = counter.incrementAndGet();
                    if (count % reportAtEach == 0) {
                        publishProgress(count);
                    }
                } catch (VerIDCoreException e) {
                    errorRef.set(e);
                    break;
                }
            }
            return faces;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (progressCallback != null) {
                progressCallback.onProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(IRecognizable[] faces) {
            VerIDCoreException error = errorRef.get();
            if (error != null && errorCallback != null) {
                errorCallback.onError(error);
            } else if (error == null && callback != null) {
                callback.onFacesGenerated(faces, startIndex);
            }
        }
    }

    private interface GenerateFacesCallback {
        void onFacesGenerated(IRecognizable[] faces, int startIndex);
    }

    private interface ProgressCallback {
        void onProgress(int itemCount);
    }

    private interface ErrorCallback {
        void onError(VerIDCoreException e);
    }
}