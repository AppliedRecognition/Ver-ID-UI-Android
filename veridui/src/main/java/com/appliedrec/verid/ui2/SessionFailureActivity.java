package com.appliedrec.verid.ui2;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.ui2.databinding.ActivityFailureBinding;

public class SessionFailureActivity extends SessionResultActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityFailureBinding viewBinding = ActivityFailureBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        getSessionResult().getError().ifPresent(error -> {
            if (error.getCode() == VerIDSessionException.Code.TIMEOUT) {
                viewBinding.errorTextView.setText(translate("Session timed out"));
            } else {
                viewBinding.errorTextView.setText(translate("Session failed"));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session_failure, menu);
        menu.findItem(R.id.action_tips).setTitle(translate("Tips"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_tips) {
            Intent intent = new Intent(this, TipsActivity.class);
            intent.putExtra(SessionActivity.EXTRA_SESSION_ID, getIntent().getLongExtra(SessionActivity.EXTRA_SESSION_ID, -1));
            startActivity(intent);
            return true;
        }
        return false;
    }

}
