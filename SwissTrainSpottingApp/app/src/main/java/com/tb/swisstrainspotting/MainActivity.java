package com.tb.swisstrainspotting;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tb.swisstrainspotting.ui.AcquisitionMode;

/**
 * Launcher activity — navigation hub that passes the acquisition choice to {@link ImageClassificationActivity}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Present two buttons (Image via system photo picker, Camera via device camera).</li>
 *   <li>Encode the chosen mode as an intent extra ({@link #EXTRA_ACQUISITION_MODE}).</li>
 *   <li>Perform no acquisition logic, no permission requests, and no image processing.</li>
 * </ul>
 *
 * All image handling — EXIF correction, decode, display, preprocessing, inference, OCR, routing —
 * is delegated to {@code ImageClassificationActivity}. This activity contains no ML, no asset loading,
 * and no thread management.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_ACQUISITION_MODE = "acquisition_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ImageClassificationActivity.class);
                intent.putExtra(EXTRA_ACQUISITION_MODE, AcquisitionMode.GALLERY.name());
                startActivity(intent);
            }
        });

        findViewById(R.id.btnCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ImageClassificationActivity.class);
                intent.putExtra(EXTRA_ACQUISITION_MODE, AcquisitionMode.CAMERA.name());
                startActivity(intent);
            }
        });
    }
}