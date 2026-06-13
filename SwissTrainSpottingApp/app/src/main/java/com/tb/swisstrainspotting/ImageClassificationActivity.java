package com.tb.swisstrainspotting;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class ImageClassificationActivity extends AppCompatActivity {

    public static final String EXTRA_ACQUISITION_MODE = "acquisition_mode";
    private ImageView iPreview;
    private ActivityResultLauncher<Intent> galleryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_classification);

        iPreview = findViewById(R.id.ivPlaceholder);

        // Register the photo picker launcher for GALLERY mode.
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        loadImageFromUri(uri);
                    }
                }
        );

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            Intent intent = new Intent(ImageClassificationActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        AcquisitionMode mode = AcquisitionMode.GALLERY;
        String intentMode = getIntent().getStringExtra(EXTRA_ACQUISITION_MODE);
        if (intentMode != null) {
            try {
                mode = AcquisitionMode.valueOf(intentMode);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (mode == AcquisitionMode.GALLERY) {
            launchGalleryPicker();
        } else {
            Toast.makeText(ImageClassificationActivity.this, R.string.selection_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGalleryPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryPickerLauncher.launch(intent);
    }

    private void loadImageFromUri(Uri uri) {
        if (uri == null) return;
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            // Decode with inSampleSize for a large image to avoid OOM. 
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap != null && !bitmap.isRecycled()) {
                iPreview.setImageBitmap(bitmap);
                iPreview.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(ImageClassificationActivity.this, R.string.image_decode_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (FileNotFoundException e) {
            Toast.makeText(ImageClassificationActivity.this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
