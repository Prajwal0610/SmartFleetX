package com.example.smartfleetx.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfleetx.R;
import com.example.smartfleetx.adapter.AlertAdapter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class DriverHealthMonitorActivity extends AppCompatActivity {

    private static final String TAG = "DriverHealthMonitor";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final float DROWSY_EYE_THRESHOLD = 0.2f;
    private static final float DISTRACTED_HEAD_THRESHOLD = 20.0f;
    
    private PreviewView previewView;
    private TextView tvAttentionScore, tvDrowsinessStatus, tvDistractionStatus;
    private RecyclerView rvAlerts;
    private Button btnToggleMonitoring, btnViewHistory;

    private boolean isMonitoring = false;
    private boolean cameraReady = false;
    private int currentAttentionScore = 100;
    private Handler monitoringHandler = new Handler();
    
    private AlertAdapter alertAdapter;
    private List<String> alertsList = new ArrayList<>();
    
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    
    // ML metrics
    private float leftEyeOpenProb = 1.0f;
    private float rightEyeOpenProb = 1.0f;
    private float headYRotation = 0.0f;
    private float headXRotation = 0.0f;
    private int blinkCount = 0;
    private long lastBlinkTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_health);

        initializeViews();
        setupRecyclerView();
        setupListeners();
        loadInitialData();
        setupMLKit();
        checkCameraPermission();
    }

    private void setupMLKit() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.cameraPreview);
        tvAttentionScore = findViewById(R.id.tvAttentionScore);
        tvDrowsinessStatus = findViewById(R.id.tvDrowsinessStatus);
        tvDistractionStatus = findViewById(R.id.tvDistractionStatus);
        rvAlerts = findViewById(R.id.rvAlerts);
        btnToggleMonitoring = findViewById(R.id.btnToggleMonitoring);
        btnViewHistory = findViewById(R.id.btnViewHistory);

        tvAttentionScore.setText("100");
        tvDrowsinessStatus.setText("Alert");
        tvDistractionStatus.setText("Focused");
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required for face monitoring", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraPreview();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraPreview() {
        Preview preview = new Preview.Builder().build();
        
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isMonitoring) {
                processImageProxy(imageProxy);
            } else {
                imageProxy.close();
            }
        });

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            cameraReady = true;
            addAlert("✓ Camera ready - ML face detection active");
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImageProxy(ImageProxy imageProxy) {
        @androidx.camera.core.ExperimentalGetImage
        android.media.Image mediaImage = imageProxy.getImage();
        
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, 
                imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    analyzeFaces(faces);
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed", e);
                    imageProxy.close();
                });
        } else {
            imageProxy.close();
        }
    }

    private void analyzeFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            runOnUiThread(() -> {
                currentAttentionScore = 0;
                updateUI();
                addAlert("⚠ No face detected - Driver missing!");
            });
            return;
        }

        Face face = faces.get(0);
        
        // Get eye probabilities
        if (face.getLeftEyeOpenProbability() != null) {
            leftEyeOpenProb = face.getLeftEyeOpenProbability();
        }
        if (face.getRightEyeOpenProbability() != null) {
            rightEyeOpenProb = face.getRightEyeOpenProbability();
        }

        // Get head rotation
        headYRotation = face.getHeadEulerAngleY(); // Left/Right
        headXRotation = face.getHeadEulerAngleX(); // Up/Down

        // Calculate attention score
        calculateAttentionScore();
        
        runOnUiThread(this::updateUI);
    }

    private void calculateAttentionScore() {
        int score = 100;
        
        // Eye closure detection (drowsiness)
        float avgEyeOpen = (leftEyeOpenProb + rightEyeOpenProb) / 2.0f;
        if (avgEyeOpen < DROWSY_EYE_THRESHOLD) {
            score -= 40; // Heavy penalty for closed eyes
            detectBlink();
        } else if (avgEyeOpen < 0.5f) {
            score -= 20; // Moderate penalty for partially closed eyes
        }

        // Head pose detection (distraction)
        float headRotation = Math.abs(headYRotation);
        if (headRotation > DISTRACTED_HEAD_THRESHOLD) {
            score -= 30; // Looking away
        } else if (headRotation > 10.0f) {
            score -= 15; // Slightly distracted
        }

        // Vertical head tilt
        if (Math.abs(headXRotation) > 15.0f) {
            score -= 10; // Head down or up
        }

        currentAttentionScore = Math.max(0, Math.min(100, score));
    }

    private void detectBlink() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime > 200 && currentTime - lastBlinkTime < 5000) {
            blinkCount++;
            if (blinkCount > 15) { // Excessive blinking
                addAlert("⚠ Excessive blinking detected");
                blinkCount = 0;
            }
        }
        lastBlinkTime = currentTime;
    }

    private void updateUI() {
        tvAttentionScore.setText(String.valueOf(currentAttentionScore));
        
        float avgEyeOpen = (leftEyeOpenProb + rightEyeOpenProb) / 2.0f;
        
        if (currentAttentionScore < 50) {
            tvDrowsinessStatus.setText("Critical ⚠⚠");
            tvDistractionStatus.setText("Severely Distracted");
            addAlert("🚨 CRITICAL: Pull over immediately!");
        } else if (avgEyeOpen < DROWSY_EYE_THRESHOLD) {
            tvDrowsinessStatus.setText("Drowsy ⚠");
            tvDistractionStatus.setText("Eyes Closing");
            addAlert("⚠ Drowsiness detected - Stay alert!");
        } else if (Math.abs(headYRotation) > DISTRACTED_HEAD_THRESHOLD) {
            tvDrowsinessStatus.setText("Alert");
            tvDistractionStatus.setText("Looking Away ⚠");
            addAlert("⚠ Eyes on the road!");
        } else if (currentAttentionScore < 85) {
            tvDrowsinessStatus.setText("Moderate");
            tvDistractionStatus.setText("Partially Focused");
        } else {
            tvDrowsinessStatus.setText("Alert");
            tvDistractionStatus.setText("Focused");
        }
    }

    private void setupRecyclerView() {
        alertAdapter = new AlertAdapter(alertsList);
        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        rvAlerts.setAdapter(alertAdapter);
    }

    private void setupListeners() {
        btnToggleMonitoring.setOnClickListener(v -> toggleMonitoring());
        btnViewHistory.setOnClickListener(v -> viewHistory());
    }

    private void loadInitialData() {
        alertsList.add("✓ ML Kit initialized - Ready to monitor");
        alertAdapter.notifyDataSetChanged();
    }

    private void toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
    }

    private void startMonitoring() {
        if (!cameraReady) {
            Toast.makeText(this, "Please wait for camera to initialize", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isMonitoring = true;
        btnToggleMonitoring.setText("Stop Monitoring");
        addAlert("✓ AI Monitoring started - Analyzing driver state");
        Toast.makeText(this, "Real-time face detection active", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        isMonitoring = false;
        btnToggleMonitoring.setText("Start Monitoring");
        addAlert("⊗ Monitoring stopped");
        
        // Reset values
        currentAttentionScore = 100;
        leftEyeOpenProb = 1.0f;
        rightEyeOpenProb = 1.0f;
        headYRotation = 0.0f;
        updateUI();
    }

    private void addAlert(String alert) {
        alertsList.add(0, alert);
        if (alertsList.size() > 50) alertsList.remove(alertsList.size() - 1);
        runOnUiThread(() -> {
            alertAdapter.notifyDataSetChanged();
            rvAlerts.smoothScrollToPosition(0);
        });
    }

    private void viewHistory() {
        addAlert("ℹ Viewing monitoring history...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}
