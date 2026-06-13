package com.tb.swisstrainspotting;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ImageClassificationActivity extends AppCompatActivity {

    public static final String EXTRA_ACQUISITION_MODE = "acquisition_mode";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private ImageView iPreview;
    private ActivityResultLauncher<Intent> galleryPickerLauncher;
    private Uri currentCameraUri;
    private ActivityResultLauncher<Uri> cameraLauncher;

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

        // Create temporary file for camera output
        currentCameraUri = createTempFileForCamera();

        // Register the camera launcher before we check mode
        if (currentCameraUri != null) {
            final Uri outputUri = currentCameraUri;
            cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                captured -> {
                    if (captured) {
                        loadImageFromUri(outputUri);
                    } else {
                        Toast.makeText(ImageClassificationActivity.this, R.string.camera_capture_cancelled, Toast.LENGTH_SHORT).show();
                    }
                }
            );
        }

        // Read acquisition mode from intent
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
        } else if (cameraLauncher != null) {
            cameraPermissionAndLaunch();
        } else if (mode == AcquisitionMode.CAMERA) {
            // cameraLauncher is null means temp file creation failed
            Toast.makeText(this, R.string.camera_temp_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createTempFileForCamera() {
        String prefix = "cam_";
        String suffix = ".jpg";
        try {
            File tempFile = File.createTempFile(prefix, suffix, getCacheDir());
            return FileProvider.getUriForFile(
                    ImageClassificationActivity.this,
                    "com.tb.swisstrainspotting.fileprovider",
                    tempFile
            );
        } catch (Exception e) {
            Toast.makeText(ImageClassificationActivity.this, R.string.camera_temp_file_error, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void cameraPermissionAndLaunch() {
        int check = ContextCompat.checkSelfPermission(
                ImageClassificationActivity.this, Manifest.permission.CAMERA);
        if (check == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(currentCameraUri);
        } else {
            ActivityCompat.requestPermissions(
                    ImageClassificationActivity.this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
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
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap != null && !bitmap.isRecycled()) {
                iPreview.setImageBitmap(bitmap);
                iPreview.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, R.string.image_decode_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (FileNotFoundException e) {
            Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            boolean permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionGranted && currentCameraUri != null && cameraLauncher != null) {
                cameraLauncher.launch(currentCameraUri);
            } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
