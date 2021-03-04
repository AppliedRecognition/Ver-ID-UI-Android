package com.appliedrec.verid.ui2;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

public abstract class AbstractSessionFailureActivity extends SessionResultActivity {

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
