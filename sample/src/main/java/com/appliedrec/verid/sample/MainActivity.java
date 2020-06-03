package com.appliedrec.verid.sample;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core.VerID;

public class MainActivity extends AppCompatActivity implements IVerIDLoadObserver {

    private static final int REQUEST_CODE_ONERROR = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        AsyncTask.execute(() -> {
            try {
                String[] users = verid.getUserManagement().getUsers();
                runOnUiThread(() -> {
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
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    Intent intent = new Intent(this, ErrorActivity.class);
                    intent.putExtra(Intent.EXTRA_TEXT, e.getLocalizedMessage());
                    startActivityForResult(intent, REQUEST_CODE_ONERROR);
                });
            }
        });
    }

    @Override
    public void onVerIDUnloaded() {

    }
}
