package com.appliedrec.verid.sample;

import android.content.Context;
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
import com.appliedrec.verid.core2.IdentificationResult;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class IdentificationDemoActivity extends AppCompatActivity implements IVerIDLoadObserver, VerIDSessionDelegate {

    private ActivityIdentificationDemoBinding viewBinding;
    private VerID verID;
    static final int MAX_FACES = 1000000;
    private IRecognizable[] generatedFaces = new IRecognizable[0];
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);

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
        new Thread(() -> {
            try {
                FaceRecognition faceRecognition = (FaceRecognition) verID.getFaceRecognition();
                VerIDFaceTemplateVersion faceTemplateVersion = verID.getUserManagement().getLatestCommonFaceTemplateVersion();
                IRecognizable[] randomFaces = faceRecognition.generateRandomFaceTemplates(facesToGenerate, faceTemplateVersion);
                IRecognizable[] userFaces = verID.getUserManagement().getFacesOfUser(VerIDUser.DEFAULT_USER_ID, faceTemplateVersion);
                generatedFaces = new IRecognizable[userFaces.length + randomFaces.length];
                System.arraycopy(randomFaces, 0, generatedFaces, 0, randomFaces.length);
                System.arraycopy(userFaces, 0, generatedFaces, randomFaces.length, userFaces.length);
                if (viewBinding == null) {
                    return;
                }
                viewBinding.label.post(() -> {
                    viewBinding.progressBar.setVisibility(View.GONE);
                    viewBinding.label.setText("Faces to generate");
                    viewBinding.facesTextField.setVisibility(View.VISIBLE);
                    startSession();
                });
            } catch (Exception e) {
                if (viewBinding == null) {
                    return;
                }
                viewBinding.label.post(() -> {
                    viewBinding.progressBar.setVisibility(View.GONE);
                    viewBinding.label.setText("Faces to generate");
                    viewBinding.facesTextField.setVisibility(View.VISIBLE);
                });
                showAlert("Failed to generate faces");
            }
        }).start();
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
            try {
                UserIdentification userIdentification = new UserIdentification(verID);
                double faceCount = generatedFaces.length;
                if (viewBinding == null) {
                    return;
                }
                viewBinding.progressBar.setMax((int) faceCount);
                viewBinding.label.setText(String.format("Identifying you in %d faces", (int) faceCount));
                int faceTemplateVersion = verID.getUserManagement().getLatestCommonFaceTemplateVersion().serialNumber(false);
                AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
                IRecognizable challengeFace = null;
                for (IRecognizable capturedFace : faceCapture.getFaces()) {
                    if (capturedFace.getVersion() == faceTemplateVersion) {
                        challengeFace = capturedFace;
                        break;
                    }
                }
                if (challengeFace == null) {
                    throw new VerIDCoreException(VerIDCoreException.Code.MISSING_FACE_TEMPLATE_VERSIONS);
                }
                userIdentification.findFacesSimilarTo(challengeFace, generatedFaces, null, new UserIdentificationCallbacks() {

                    @Override
                    public void onComplete(IdentifiedFace[] identifiedFaces) {
                        long elapsedTime = System.currentTimeMillis() - startTime.get();
                        userIdentification.close();
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
                                Stream<String> identifiedUsers = Arrays.stream(identifiedFaces)
                                        .map(face -> {
                                            try {
                                                return verID.getUserManagement().getUserInFace(face.getFace());
                                            } catch (Exception e) {
                                                return null;
                                            }
                                        })
                                        .filter(Objects::nonNull);
                                if (identifiedUsers.anyMatch(user -> user.equals(VerIDUser.DEFAULT_USER_ID))) {
                                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(IdentificationDemoActivity.this);
                                    try {
                                        RoundedBitmapDrawable drawable = new ProfilePhotoHelper(IdentificationDemoActivity.this).getProfilePhotoDrawable((int) (100f * getResources().getDisplayMetrics().density));
                                        ImageView imageView = new ImageView(IdentificationDemoActivity.this);
                                        imageView.setImageDrawable(drawable);
                                        alertDialogBuilder.setView(imageView);
                                    } catch (Exception ignore) {
                                    }
                                    AlertDialog alertDialog = alertDialogBuilder
                                            .setMessage(String.format("You've been identified among %.0f faces in %d ms", faceCount, elapsedTime))
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
                            }
                        }
                    }

                    @Override
                    public void onProgress(double progress) {
                        if (viewBinding == null) {
                            return;
                        }
                        viewBinding.progressBar.setVisibility(View.VISIBLE);
                        viewBinding.progressBar.setProgress((int)(progress * faceCount));
                    }

                    @Override
                    public void onError(Throwable error) {
                        userIdentification.close();
                        if (viewBinding == null) {
                            return;
                        }
                        invalidateOptionsMenu();
                        viewBinding.label.setText("Faces to generate");
                        viewBinding.progressBar.setVisibility(View.GONE);
                        showAlert("Face comparison failed");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
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
}