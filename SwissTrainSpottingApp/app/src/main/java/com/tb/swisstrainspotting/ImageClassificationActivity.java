package com.tb.swisstrainspotting;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ImageClassificationActivity extends AppCompatActivity {

    public static final String EXTRA_ACQUISITION_MODE = "acquisition_mode";
    public static final String EXTRA_PICKER_RESULT_URI = "picker_result_uri";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final String STATE_IMAGE_URI = "state_image_uri";

    private ImageView iPreview;
    private ActivityResultLauncher<Intent> galleryPickerLauncher;
    private Uri currentCameraUri;
    private Uri currentImageUri;
    private ActivityResultLauncher<Uri> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_classification);

        iPreview = findViewById(R.id.ivPlaceholder);

        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        loadImageFromUri(uri);
                    }
                }
        );

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            Intent intent = new Intent(ImageClassificationActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        currentCameraUri = createTempFileForCamera();

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

        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_IMAGE_URI);
            if (savedUri != null) {
                currentImageUri = Uri.parse(savedUri);
                loadImageFromUri(currentImageUri);
                return;
            }
        }

        String pickerResultUri = getIntent().getStringExtra(EXTRA_PICKER_RESULT_URI);
        if (pickerResultUri != null) {
            loadImageFromUri(Uri.parse(pickerResultUri));
            return;
        }

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
            Toast.makeText(this, R.string.camera_temp_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentImageUri != null) {
            outState.putString(STATE_IMAGE_URI, currentImageUri.toString());
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
        if (uri == null) {
            return;
        }
        currentImageUri = uri;

        Bitmap bitmap;
        try (InputStream decodeStream = getContentResolver().openInputStream(uri)) {
            if (decodeStream == null) {
                Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            bitmap = BitmapFactory.decodeStream(decodeStream);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
            return;
        } catch (IOException e) {
            Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        if (bitmap == null || bitmap.isRecycled()) {
            Toast.makeText(this, R.string.image_decode_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try (InputStream exifStream = getContentResolver().openInputStream(uri)) {
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                bitmap = applyExifOrientation(bitmap, exif);
            }
        } catch (IOException ignored) {
        }

        iPreview.setImageBitmap(bitmap);
        iPreview.setVisibility(View.VISIBLE);
    }

    private Bitmap applyExifOrientation(Bitmap bitmap, ExifInterface exif) throws IOException {
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270f);
                matrix.postScale(-1f, 1f);
                break;
            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
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
