package com.appliedrec.verid.sample;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.appliedrec.verid.core.SessionResult;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.ui.VerIDSessionActivity;


public class MainActivity extends AppCompatActivity implements VerIDFactoryDelegate {

    private static final int REQUEST_CODE_ONERROR = 0;

    private int veridInstanceId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) {
            int veridInstanceId = savedInstanceState.getInt(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, -1);
            if (veridInstanceId > -1) {
                try {
                    loadRegisteredUsers(VerID.getInstance(veridInstanceId));
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        createVerID();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, veridInstanceId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ONERROR && resultCode == RESULT_OK) {
            createVerID();
        }
    }

    private void createVerID() {
        VerIDFactory verIDFactory = new VerIDFactory(this, this);
        verIDFactory.createVerID();
    }

    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory factory, VerID environment) {
        loadRegisteredUsers(environment);
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory factory, Exception error) {
        showError("Failed to create Ver-ID environment");
    }

    private void loadRegisteredUsers(final VerID verID) {
        veridInstanceId = verID.getInstanceId();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String[] users = verID.getUserManagement().getUsers();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent;
                            if (users.length > 0) {
                                intent = new Intent(MainActivity.this, RegisteredUserActivity.class);
                            } else {
                                intent = new Intent(MainActivity.this, IntroActivity.class);
                            }
                            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                            startActivity(intent);
                            finish();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError("Failed to retrieve registered users");
                        }
                    });
                }
            }
        });
    }

    private void showError(String message) {
        Intent intent = new Intent(this, ErrorActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivityForResult(intent, REQUEST_CODE_ONERROR);
    }
}
