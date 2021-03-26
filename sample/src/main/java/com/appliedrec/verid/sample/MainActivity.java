package com.appliedrec.verid.sample;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.espresso.IdlingResource;

import com.appliedrec.verid.core2.VerID;

import io.reactivex.rxjava3.disposables.Disposable;

public class MainActivity extends AppCompatActivity implements IVerIDLoadObserver, IdlingResource {

    private static final int REQUEST_CODE_ONERROR = 0;
    private boolean veridLoadFinished = false;
    private ResourceCallback resourceCallback;
    private Disposable getUsersDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getUsersDisposable != null && !getUsersDisposable.isDisposed()) {
            getUsersDisposable.dispose();
        }
        getUsersDisposable = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ONERROR && resultCode == RESULT_OK) {
            finish();
        }
    }

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
                    startActivityForResult(intent, REQUEST_CODE_ONERROR);
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
