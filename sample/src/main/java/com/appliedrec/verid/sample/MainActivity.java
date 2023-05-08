package com.appliedrec.verid.sample;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.espresso.IdlingResource;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.sample.databinding.ActivityMainBinding;

import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements IVerIDLoadObserver, IdlingResource {

    private boolean veridLoadFinished = false;
    private ResourceCallback resourceCallback;
    private Disposable getUsersDisposable;
    private ActivityMainBinding viewBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getUsersDisposable != null && !getUsersDisposable.isDisposed()) {
            getUsersDisposable.dispose();
        }
        viewBinding = null;
        getUsersDisposable = null;
    }

    private final ActivityResultLauncher<Intent> errorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            finish();
        }
    });

    @Override
    public void onVerIDLoaded(VerID verid) {
        getUsersDisposable = verid.getUserManagement().getUsersSingle().subscribe(
                users -> {
                    if (isDestroyed()) {
                        return;
                    }
                    Intent intent;
                    if (users.length > 0) {
                        intent = new Intent(this, RegisteredUserActivity.class);
                    } else {
                        intent = new Intent(this, IntroActivity.class);
                    }
                    startActivity(intent);
                    veridLoadFinished = true;
                    if (resourceCallback != null) {
                        resourceCallback.onTransitionToIdle();
                    }
                    finish();
                },
                error -> {
                    if (isDestroyed()) {
                        return;
                    }
                    Intent intent = new Intent(this, ErrorActivity.class);
                    intent.putExtra(Intent.EXTRA_TEXT, error.toString());
                    errorLauncher.launch(intent);
                    veridLoadFinished = true;
                    if (resourceCallback != null) {
                        resourceCallback.onTransitionToIdle();
                    }
                }
        );
    }

    @Override
    public void onVerIDUnloaded() {

    }

    @Override
    public void onVerIDLoadProgress(float progress) {
        if (viewBinding == null) {
            return;
        }
        viewBinding.determinateProgressBar.setVisibility(View.VISIBLE);
        viewBinding.determinateProgressBar.setMax(100);
        viewBinding.determinateProgressBar.setProgress((int)(progress * 100f));
    }

    @Override
    public String getName() {
        return "Main Activity";
    }

    @Override
    public boolean isIdleNow() {
        return veridLoadFinished;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        resourceCallback = callback;
    }
}
