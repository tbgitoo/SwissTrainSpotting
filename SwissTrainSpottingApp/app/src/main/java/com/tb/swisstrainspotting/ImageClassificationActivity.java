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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.tb.swisstrainspotting.imageprocess.ImagePreprocessor;
import com.tb.swisstrainspotting.ocr.MlKitOcrAnalyzer;
import com.tb.swisstrainspotting.ocr.OcrAnalyzer;
import com.tb.swisstrainspotting.ocr.OcrResult;
import com.tb.swisstrainspotting.onnx.AllowedSetLoader;
import com.tb.swisstrainspotting.onnx.ClassificationRouter;
import com.tb.swisstrainspotting.onnx.ModelProfile;
import com.tb.swisstrainspotting.onnx.OnnxClassifier;
import com.tb.swisstrainspotting.onnx.RoutedClassificationResult;
import com.tb.swisstrainspotting.ui.AcquisitionMode;
import com.tb.swisstrainspotting.ui.ProfileConfig;
import com.tb.swisstrainspotting.ui.RoutedResultFormatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;

/**
 * Classification screen: acquires an image, runs generic + specialized ONNX inference with
 * presentation-aware routing, and optionally extracts text via ML Kit OCR.
 *
 * <p>Lifecycle: on {@link #onDestroy()} all executors and classifiers are shut down;
 * active classification generations are discarded via a session counter so stale callbacks
 * never reach the UI after rotation or activity teardown.
 *
 * <p>Threading: preprocessing and inference run on {@code inferenceExecutor} (single-thread);
 * OCR runs on {@code ocrExecutor}. Results always flow back through {@code runOnUiThread}.
 *
 * <p>Image acquisition supports both gallery pick (photo picker) and camera capture.
 * EXIF orientation is corrected before inference; the working Bitmap lives in memory only.
 *
 * <p>Cross-module contract: consumes planar NCHW {@code float[]} from {@link ImagePreprocessor},
 * delegates to {@link OnnxClassifier} for both generic (MobileNetV2) and profile-scoped models,
 * and uses {@link ClassificationRouter} with a per-profile allowed set for result presentation.
 */
public class ImageClassificationActivity extends AppCompatActivity {

    public static final String EXTRA_ACQUISITION_MODE = "acquisition_mode";
    public static final String EXTRA_PICKER_RESULT_URI = "picker_result_uri";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final String STATE_IMAGE_URI = "state_image_uri";

    private ImageView iPreview;
    private TextView tvClassificationResult;
    private View llOcrSection;
    private TextView tvOcrLabel;
    private TextView tvOcrResult;
    private ActivityResultLauncher<Intent> galleryPickerLauncher;
    private Uri currentCameraUri;
    private Uri currentImageUri;
    private ActivityResultLauncher<Uri> cameraLauncher;

    // Phase 5D: generic + specialized classifiers with presentation-aware routing.
    private OnnxClassifier genericClassifier;
    private OnnxClassifier specializedClassifier;
    private ClassificationRouter.AllowedSet allowedSet;
    private ProfileConfig profileConfig;

    private ExecutorService inferenceExecutor;
    private ExecutorService ocrExecutor;
    private OcrAnalyzer ocrAnalyzer;
    private OcrResult lastOcrResult;
    private int classificationGeneration = 0;

    // Phase 6D testability seam: allows instrumentation tests to inject a stub OCR analyzer.
    static OcrAnalyzer ocrAnalyzerStub = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_classification);

        iPreview = findViewById(R.id.ivPlaceholder);
        tvClassificationResult = findViewById(R.id.tv_classification_result);
        llOcrSection = findViewById(R.id.ll_ocr_section);
        tvOcrLabel = findViewById(R.id.tv_ocr_label);
        tvOcrResult = findViewById(R.id.tv_ocr_result);

        inferenceExecutor = Executors.newSingleThreadExecutor();
        ocrExecutor = Executors.newSingleThreadExecutor();
        if (ocrAnalyzerStub != null) {
            ocrAnalyzer = ocrAnalyzerStub;
        } else {
            ocrAnalyzer = new MlKitOcrAnalyzer();
        }

        try {
            genericClassifier = new OnnxClassifier(getApplicationContext());
        } catch (IOException e) {
            tvClassificationResult.setText(R.string.classifier_init_failed);
        }

        try {
            ModelProfile specialtyProfile = ModelProfile.load(getApplicationContext(), "swiss_trains");
            specializedClassifier = new OnnxClassifier(getApplicationContext(), specialtyProfile);
            String profileId = specialtyProfile.getId();

            // Phase 5E: allowed-set loaded from asset by profile ID, not from JSON metadata.
            java.util.Set<String> labelSet = AllowedSetLoader.load(getApplicationContext(), profileId);
            allowedSet = new ClassificationRouter.AllowedSet(labelSet.toArray(new String[0]));

            // App-side domain config for conditional messaging.
            try {
                profileConfig = ProfileConfig.load(getApplicationContext(), profileId);
            } catch (IOException | JSONException ignored) {
                // Graceful degradation: domain display name falls back to empty.
            }
        } catch (IOException | JSONException e) {
            tvClassificationResult.setText(getString(R.string.routing_error, e.getMessage()));
        }

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
    protected void onDestroy() {
        classificationGeneration++;
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdownNow();
            inferenceExecutor = null;
        }
        if (ocrExecutor != null) {
            ocrExecutor.shutdownNow();
            ocrExecutor = null;
        }
        if (ocrAnalyzer != null) {
            ocrAnalyzer.close();
            ocrAnalyzer = null;
        }
        lastOcrResult = null;
        if (genericClassifier != null) {
            genericClassifier.close();
            genericClassifier = null;
        }
        if (specializedClassifier != null) {
            specializedClassifier.close();
            specializedClassifier = null;
        }
        super.onDestroy();
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
        runClassification(bitmap);
    }

    private void runClassification(Bitmap bitmap) {
        if (genericClassifier == null
                || specializedClassifier == null
                || inferenceExecutor == null
                || bitmap == null
                || bitmap.isRecycled()) {
            return;
        }

        final int generation = ++classificationGeneration;
        lastOcrResult = null;
        tvClassificationResult.setText(R.string.classifying);
        
        if (llOcrSection != null) {
            llOcrSection.setVisibility(View.GONE);
        }
        
        startOcrInBackground(bitmap, generation);

        inferenceExecutor.execute(() -> {
            try {
                float[] tensor = ImagePreprocessor.preprocess(bitmap);
                RoutedClassificationResult routedResult = ClassificationRouter.runAndRoute(
                        tensor,
                        genericClassifier::classify,
                        specializedClassifier::classify,
                        allowedSet
                );

                runOnUiThread(() -> {
                    if (!shouldApplySessionResult(generation)) {
                        return;
                    }
                    applyRoutedResult(routedResult);
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (!shouldApplySessionResult(generation)) {
                        return;
                    }
                    tvClassificationResult.setText(R.string.classification_failed);
                });
            }
        });
    }

    private void startOcrInBackground(Bitmap bitmap, int generation) {
        if (ocrExecutor == null || ocrAnalyzer == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }

        ocrExecutor.execute(() -> {
            OcrResult result;
            try {
                result = ocrAnalyzer.recognize(bitmap);
            } catch (RuntimeException e) {
                result = OcrResult.empty();
            }
            if (result == null) {
                result = OcrResult.empty();
            }

            final OcrResult ocrResult = result;
            runOnUiThread(() -> {
                if (!shouldApplySessionResult(generation)) {
                    return;
                }
                applyOcrResult(ocrResult);
            });
        });
    }

    public void applyOcrResult(OcrResult result) {
        if (result == null || result.isEmpty()) {
            lastOcrResult = null;
            if (llOcrSection != null) {
                llOcrSection.setVisibility(View.GONE);
            }
            return;
        }
        lastOcrResult = result;
        tvOcrLabel.setText(R.string.ocr_label_experimental);
        tvOcrResult.setText(result.getText());
        if (llOcrSection != null) {
            llOcrSection.setVisibility(View.VISIBLE);
        }
    }

    public void applyRoutedResult(RoutedClassificationResult routedResult) {
        tvClassificationResult.setText(formatRoutedResult(routedResult, profileConfig));
    }
    public String formatRoutedResult(RoutedClassificationResult routedResult, ProfileConfig config) {
        return RoutedResultFormatter.format(this, routedResult, config);
    }

    // Phase 6D: testability seam setter — allows instrumentation tests to inject a stub analyzer.
    public static void setOcrAnalyzerForTesting(OcrAnalyzer stub) {
        ocrAnalyzerStub = stub;
    }

    private boolean shouldApplySessionResult(int generation) {
        return generation == classificationGeneration && !isFinishing() && !isDestroyed();
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
