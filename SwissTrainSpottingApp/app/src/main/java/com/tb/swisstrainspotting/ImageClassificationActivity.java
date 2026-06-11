package com.tb.swisstrainspotting;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ImageClassificationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_classification);
        
        // Enable the default ActionBar back arrow
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
}