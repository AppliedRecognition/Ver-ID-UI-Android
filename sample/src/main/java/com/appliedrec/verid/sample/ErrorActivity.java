package com.appliedrec.verid.sample;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ErrorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);
        Intent intent = getIntent();
        if (intent != null) {
            String message = intent.getStringExtra(Intent.EXTRA_TEXT);
            TextView textView = findViewById(R.id.textView);
            textView.setText(message);
        }
    }

    public void onReload(View view) {
        setResult(RESULT_OK);
        finish();
    }
}
