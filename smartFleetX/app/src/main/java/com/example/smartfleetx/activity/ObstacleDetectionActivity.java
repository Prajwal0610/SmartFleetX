package com.example.smartfleetx.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartfleetx.R;
import com.example.smartfleetx.ai.YOLOv8Detector;
import com.example.smartfleetx.database.DatabaseHelper;
import com.example.smartfleetx.ml.SeverityClassifier;
import com.example.smartfleetx.model.IncidentData;
import com.example.smartfleetx.model.SensorLog;
import com.example.smartfleetx.network.RetrofitClient;
import com.example.smartfleetx.security.DataIntegrityVerifier;
import com.example.smartfleetx.service.AccidentDetector;
import com.example.smartfleetx.service.EmergencyAlertManager;
import com.example.smartfleetx.service.OfflineSyncManager;
import com.example.smartfleetx.service.ReportGenerator;
import com.example.smartfleetx.service.SensorDataLogger;
import com.example.smartfleetx.service.SystemHealthMonitor;
import com.example.smartfleetx.ui.OverlayView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import androidx.core.content.FileProvider;

public class ObstacleDetectionActivity extends AppCompatActivity {

    private static final String TAG = "ObstacleDetectionActivity";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
    };

    private EditText etIpAddress;
    private Button btnConnect;
    private WebView wvVideoStream;
    private TextView tvStreamStatus, tvObstacleStatus, tvDistance;
    private ProgressBar pbStreamLoading;
    private ImageView ivObstacleIcon;
    private OverlayView overlayView;
    private FloatingActionButton fabMarkIncident;

    // Recording UI
    private Button btnStartRecording, btnStopRecording;
    private TextView tvRecordingTimer, tvRecordingStatus;

    // Recording engine
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private boolean isRecording = false;
    private final AtomicBoolean muxerStarted = new AtomicBoolean(false);
    private long recordingStartTimeUs = 0;
    private int recordingElapsedSeconds = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Handler frameHandler = new Handler(Looper.getMainLooper());
    private Runnable frameRunnable;
    private static final int FRAME_RATE = 5;          // fps
    private static final int FRAME_INTERVAL_MS = 1000 / FRAME_RATE;
    private static final int VIDEO_WIDTH  = 640;
    private static final int VIDEO_HEIGHT = 360;
    private static final int VIDEO_BITRATE = 800_000;
    private File currentOutputFile;
    private long recordingStartEpochMs = 0;  // wall-clock start (for PDF timestamps)
    private long recordingStopEpochMs  = 0;  // wall-clock stop  (for PDF timestamps)

    // Paint for on-frame timestamp overlay
    private android.graphics.Paint tsPaint;
    private android.graphics.Paint tsBgPaint;

    private YOLOv8Detector detector;
    private boolean isStreaming = false;
    private Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable statusRunnable;
    private Random random = new Random();

    // Reusable bitmaps for WebView frame capture — avoids allocating a new
    // Bitmap on every detection cycle / recording frame (major GC pressure source)
    private Bitmap captureWebViewBitmap = null;
    private android.graphics.Canvas captureCanvas = null;
    private int captureWidth = 0;
    private int captureHeight = 0;

    // Single-thread executor for YOLO inference — prevents thread-per-second spawning
    private java.util.concurrent.ExecutorService detectionExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // Dashcam Integrated Components
    private AccidentDetector accidentDetector;
    private SensorDataLogger sensorDataLogger;
    private EmergencyAlertManager emergencyAlertManager;
    private SystemHealthMonitor systemHealthMonitor;
    private boolean servicesBound = false;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private float currentSpeed = 0f;

    private DatabaseHelper databaseHelper;
    private SeverityClassifier severityClassifier;
    private DataIntegrityVerifier integrityVerifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obstacle_detection);

        databaseHelper = new DatabaseHelper(this);
        severityClassifier = new SeverityClassifier();
        integrityVerifier = new DataIntegrityVerifier();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeViews();
        setupWebView();
        setupListeners();
        initTimestampPaint();

        // Disable connect button and show loading until TFLite model is ready
        btnConnect.setEnabled(false);
        btnConnect.setText("Loading AI Model…");

        // Initialize YOLOv8 detector on a background thread to avoid blocking the main thread.
        // GPU delegate + model loading can take 3-6 seconds (causes ~380 skipped frames if on main thread).
        new Thread(() -> {
            YOLOv8Detector loadedDetector = null;
            try {
                loadedDetector = new YOLOv8Detector(ObstacleDetectionActivity.this);
            } catch (IOException e) {
                Log.e(TAG, "Model loading failed", e);
            }
            final YOLOv8Detector finalDetector = loadedDetector;
            runOnUiThread(() -> {
                detector = finalDetector;
                btnConnect.setEnabled(true);
                btnConnect.setText("Connect");
                if (finalDetector == null) {
                    Toast.makeText(this, "AI Model loading failed — obstacle detection unavailable",
                            Toast.LENGTH_LONG).show();
                } else {
                    Log.d(TAG, "YOLOv8 model loaded successfully on background thread");
                }
            });
        }).start();

        if (locationPermissionsGranted()) {
            startLocationUpdates();
        }
        bindAccidentDetectionServices();
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private void initializeViews() {
        etIpAddress = findViewById(R.id.etIpAddress);
        btnConnect = findViewById(R.id.btnConnect);
        wvVideoStream = findViewById(R.id.wvVideoStream);
        tvStreamStatus = findViewById(R.id.tvStreamStatus);
        tvObstacleStatus = findViewById(R.id.tvObstacleStatus);
        tvDistance = findViewById(R.id.tvDistance);
        pbStreamLoading = findViewById(R.id.pbStreamLoading);
        ivObstacleIcon = findViewById(R.id.ivObstacleIcon);
        overlayView = findViewById(R.id.overlayView);
        fabMarkIncident = findViewById(R.id.fabMarkIncident);

        // Recording UI
        btnStartRecording  = findViewById(R.id.btnStartRecording);
        btnStopRecording   = findViewById(R.id.btnStopRecording);
        tvRecordingTimer   = findViewById(R.id.tvRecordingTimer);
        tvRecordingStatus  = findViewById(R.id.tvRecordingStatus);

        // Back button on toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /** Prepare Paint objects for the timestamp watermark burned into video frames. */
    private void initTimestampPaint() {
        tsPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tsPaint.setColor(android.graphics.Color.WHITE);
        tsPaint.setTextSize(22f);
        tsPaint.setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK);

        tsBgPaint = new android.graphics.Paint();
        tsBgPaint.setColor(android.graphics.Color.argb(140, 0, 0, 0)); // semi-transparent black
        tsBgPaint.setStyle(android.graphics.Paint.Style.FILL);
    }

    private void setupWebView() {
        WebSettings webSettings = wvVideoStream.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        wvVideoStream.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pbStreamLoading.setVisibility(View.VISIBLE);
                tvStreamStatus.setText("Loading Stream...");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pbStreamLoading.setVisibility(View.GONE);
                tvStreamStatus.setVisibility(View.GONE);
                // Guard: MJPEG streams may have already triggered the fallback in startStream().
                // Only start detection if it hasn't started yet.
                if (!isStreaming) {
                    isStreaming = true;
                    startDetectionSimulation();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                pbStreamLoading.setVisibility(View.GONE);
                tvStreamStatus.setVisibility(View.VISIBLE);
                tvStreamStatus.setText("Connection Failed");
                isStreaming = false;
            }
        });
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> {
            String ip = etIpAddress.getText().toString().trim();
            if (ip.isEmpty()) {
                etIpAddress.setError("IP required");
                return;
            }
            startStream(ip);
        });

        btnStartRecording.setOnClickListener(v -> {
            if (!isStreaming) {
                Toast.makeText(this, "Connect to stream before recording", Toast.LENGTH_SHORT).show();
                return;
            }
            startRecording();
        });

        btnStopRecording.setOnClickListener(v -> stopRecording());

        fabMarkIncident.setOnClickListener(v -> markIncident());
    }

    // ─────────────────────────────────────────────────────────────
    // VIDEO RECORDING  (MediaCodec + MediaMuxer frame capture)
    // ─────────────────────────────────────────────────────────────

    private void startRecording() {
        try {
            // Build output file
            File recDir = new File(getExternalFilesDir(null), "Recordings");
            if (!recDir.exists()) recDir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentOutputFile = new File(recDir, "Stream_" + ts + ".mp4");

            // Configure encoder
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    VIDEO_WIDTH, VIDEO_HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

            mediaMuxer = new MediaMuxer(currentOutputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoTrackIndex = -1;
            muxerStarted.set(false);

            isRecording = true;
            recordingStartTimeUs  = System.nanoTime() / 1000;
            recordingStartEpochMs = System.currentTimeMillis();   // ← wall-clock start
            recordingStopEpochMs  = 0;
            recordingElapsedSeconds = 0;

            // Update UI
            btnStartRecording.setEnabled(false);
            btnStopRecording.setEnabled(true);
            tvRecordingTimer.setVisibility(View.VISIBLE);
            tvRecordingStatus.setText("● Recording in progress…");

            startTimerTick();
            startFrameCapture();

            Log.d(TAG, "Recording started: " + currentOutputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Could not start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        recordingStopEpochMs = System.currentTimeMillis();   // ← wall-clock stop

        // Stop frame/timer handlers
        if (frameHandler != null && frameRunnable != null) frameHandler.removeCallbacks(frameRunnable);
        if (timerHandler != null && timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);

        // Flush encoder
        try {
            if (mediaCodec != null) {
                mediaCodec.signalEndOfInputStream();
                drainEncoder(true);
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping encoder", e);
        }

        // Stop muxer
        try {
            if (mediaMuxer != null && muxerStarted.get()) {
                mediaMuxer.stop();
            }
            if (mediaMuxer != null) {
                mediaMuxer.release();
                mediaMuxer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping muxer", e);
        }

        // Update UI
        btnStartRecording.setEnabled(true);
        btnStopRecording.setEnabled(false);
        tvRecordingTimer.setVisibility(View.GONE);
        tvRecordingStatus.setText("Recording saved.");

        if (currentOutputFile != null && currentOutputFile.exists()) {
            String filePath = currentOutputFile.getAbsolutePath();
            Toast.makeText(this, "✅ Video saved: " + currentOutputFile.getName(), Toast.LENGTH_LONG).show();
            tvRecordingStatus.setText("Saved: " + currentOutputFile.getName());

            // Offer to view/share
            try {
                android.net.Uri uri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider",
                        currentOutputFile);
                Intent shareIntent = new Intent(Intent.ACTION_VIEW);
                shareIntent.setDataAndType(uri, "video/mp4");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Open Recording"));
            } catch (Exception e) {
                Log.e(TAG, "No app to open video", e);
            }
        }
    }

    private void startFrameCapture() {
        frameRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    captureFrameToEncoder();
                    frameHandler.postDelayed(this, FRAME_INTERVAL_MS);
                }
            }
        };
        frameHandler.postDelayed(frameRunnable, FRAME_INTERVAL_MS);
    }

    /**
     * Draws a timestamp banner at the top-left of the bitmap.
     * Format:  "27-Apr-2026  15:03:42   ● REC 00:05"
     */
    private void drawTimestampOnBitmap(android.graphics.Bitmap bmp) {
        if (tsPaint == null) return;
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);

        String datePart = new java.text.SimpleDateFormat("dd-MMM-yyyy",
                java.util.Locale.getDefault()).format(new java.util.Date());
        String timePart = new java.text.SimpleDateFormat("HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
        int mins = recordingElapsedSeconds / 60;
        int secs = recordingElapsedSeconds % 60;
        String recPart = String.format(java.util.Locale.getDefault(),
                "  ● REC %02d:%02d", mins, secs);

        String overlay = datePart + "  " + timePart + recPart;

        float textH = tsPaint.getTextSize();
        float padding = 6f;
        float bgLeft   = 4f;
        float bgTop    = 4f;
        float bgRight  = tsPaint.measureText(overlay) + bgLeft + padding * 2;
        float bgBottom = bgTop + textH + padding * 2;

        c.drawRect(bgLeft, bgTop, bgRight, bgBottom, tsBgPaint);
        c.drawText(overlay, bgLeft + padding, bgBottom - padding - 2f, tsPaint);
    }

    private void captureFrameToEncoder() {
        if (!isRecording || wvVideoStream == null || mediaCodec == null) return;
        int wvW = wvVideoStream.getWidth();
        int wvH = wvVideoStream.getHeight();
        if (wvW <= 0 || wvH <= 0) return;

        try {
            // Reuse the same capture bitmap instead of allocating a new one every 200ms.
            if (captureWebViewBitmap == null || captureWidth != wvW || captureHeight != wvH) {
                if (captureWebViewBitmap != null) captureWebViewBitmap.recycle();
                captureWebViewBitmap = Bitmap.createBitmap(wvW, wvH, Bitmap.Config.ARGB_8888);
                captureCanvas = new android.graphics.Canvas(captureWebViewBitmap);
                captureWidth  = wvW;
                captureHeight = wvH;
            }

            wvVideoStream.draw(captureCanvas);

            // Copy the frame for the background encoding thread
            final Bitmap rawCopy = captureWebViewBitmap.copy(Bitmap.Config.ARGB_8888, false);

            new Thread(() -> {
                try {
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(rawCopy, VIDEO_WIDTH, VIDEO_HEIGHT, true);
                    rawCopy.recycle();

                    // ── Burn live timestamp into the frame ─────────────────────
                    drawTimestampOnBitmap(scaledBitmap);

                    // Convert ARGB_8888 → NV21 (YUV420)
                    byte[] yuv = bitmapToNv21(scaledBitmap, VIDEO_WIDTH, VIDEO_HEIGHT);
                    scaledBitmap.recycle();

                    long ptsUs = System.nanoTime() / 1000 - recordingStartTimeUs;

                    synchronized (ObstacleDetectionActivity.this) {
                        if (!isRecording || mediaCodec == null) return;
                        int inputBufIndex = mediaCodec.dequeueInputBuffer(10_000);
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuf = mediaCodec.getInputBuffer(inputBufIndex);
                            if (inputBuf != null) {
                                inputBuf.clear();
                                inputBuf.put(yuv);
                                mediaCodec.queueInputBuffer(inputBufIndex, 0, yuv.length, ptsUs, 0);
                            }
                        }

                        drainEncoder(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Frame capture background error", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Frame capture error", e);
        }
    }

    private void drainEncoder(boolean endOfStream) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (true) {
            int outputBufIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000);
            if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted.get()) throw new RuntimeException("Format changed twice");
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                videoTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
                muxerStarted.set(true);
            } else if (outputBufIndex >= 0) {
                ByteBuffer encodedData = mediaCodec.getOutputBuffer(outputBufIndex);
                if (encodedData == null) continue;
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                if (bufferInfo.size > 0 && muxerStarted.get()) {
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                }
                mediaCodec.releaseOutputBuffer(outputBufIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
    }

    /** Fast ARGB → NV21 (YUV420SP) conversion */
    private byte[] bitmapToNv21(Bitmap bitmap, int width, int height) {
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] yuv = new byte[width * height * 3 / 2];
        int yIdx = 0, uvIdx = width * height;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = argb[j * width + i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >>  8) & 0xFF;
                int b =  pixel        & 0xFF;
                int y  =  ((66 * r + 129 * g +  25 * b + 128) >> 8) + 16;
                yuv[yIdx++] = (byte) Math.max(0, Math.min(255, y));
                if (j % 2 == 0 && i % 2 == 0) {
                    int u = ((-38 * r -  74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r -  94 * g -  18 * b + 128) >> 8) + 128;
                    yuv[uvIdx++] = (byte) Math.max(0, Math.min(255, v)); // NV21: V then U
                    yuv[uvIdx++] = (byte) Math.max(0, Math.min(255, u));
                }
            }
        }
        return yuv;
    }

    private void startTimerTick() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    recordingElapsedSeconds++;
                    int mins = recordingElapsedSeconds / 60;
                    int secs = recordingElapsedSeconds % 60;
                    tvRecordingTimer.setText(String.format(Locale.getDefault(),
                            "● REC %02d:%02d", mins, secs));
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void bindAccidentDetectionServices() {
        Intent accidentIntent = new Intent(this, AccidentDetector.class);
        bindService(accidentIntent, accidentDetectorConnection, Context.BIND_AUTO_CREATE);

        Intent loggerIntent = new Intent(this, SensorDataLogger.class);
        bindService(loggerIntent, sensorLoggerConnection, Context.BIND_AUTO_CREATE);

        Intent alertIntent = new Intent(this, EmergencyAlertManager.class);
        bindService(alertIntent, emergencyAlertConnection, Context.BIND_AUTO_CREATE);

        Intent healthIntent = new Intent(this, SystemHealthMonitor.class);
        bindService(healthIntent, healthMonitorConnection, Context.BIND_AUTO_CREATE);

        servicesBound = true;
    }

    private ServiceConnection accidentDetectorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AccidentDetector.AccidentDetectorBinder binder = (AccidentDetector.AccidentDetectorBinder) service;
            accidentDetector = binder.getService();
            accidentDetector.setAccidentDetectionListener(accidentListener);
            accidentDetector.startMonitoring();
            Log.d(TAG, "AccidentDetector connected");
        }

        @Override public void onServiceDisconnected(ComponentName name) { accidentDetector = null; }
    };

    private ServiceConnection sensorLoggerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SensorDataLogger.SensorDataLoggerBinder binder = (SensorDataLogger.SensorDataLoggerBinder) service;
            sensorDataLogger = binder.getService();
            sensorDataLogger.setDataCaptureListener(dataCaptureListener);
            sensorDataLogger.startLogging();
            Log.d(TAG, "SensorDataLogger connected");
        }

        @Override public void onServiceDisconnected(ComponentName name) { sensorDataLogger = null; }
    };

    private ServiceConnection emergencyAlertConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            EmergencyAlertManager.EmergencyAlertBinder binder = (EmergencyAlertManager.EmergencyAlertBinder) service;
            emergencyAlertManager = binder.getService();
            Log.d(TAG, "EmergencyAlertManager connected");
        }

        @Override public void onServiceDisconnected(ComponentName name) { emergencyAlertManager = null; }
    };

    private ServiceConnection healthMonitorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SystemHealthMonitor.SystemHealthBinder binder = (SystemHealthMonitor.SystemHealthBinder) service;
            systemHealthMonitor = binder.getService();
            systemHealthMonitor.setHealthStatusListener(healthListener);
            Log.d(TAG, "SystemHealthMonitor connected");
        }

        @Override public void onServiceDisconnected(ComponentName name) { systemHealthMonitor = null; }
    };

    private AccidentDetector.AccidentDetectionListener accidentListener = (severity, confidence, gForce, sensorLog) -> {
        runOnUiThread(() -> handleAccidentDetected(severity, confidence, gForce, sensorLog));
    };

    private SensorDataLogger.DataCaptureListener dataCaptureListener = new SensorDataLogger.DataCaptureListener() {
        @Override
        public void onPreCrashDataCaptured(String eventId, List<SensorLog> preCrashData) {
            if (databaseHelper != null) databaseHelper.saveSensorLogs(eventId, preCrashData);
        }

        @Override
        public void onPostCrashDataCaptured(String eventId, List<SensorLog> postCrashData) {
            if (databaseHelper != null) {
                databaseHelper.saveSensorLogs(eventId, postCrashData);
                finalizeIncident(eventId);
            }
        }
    };

    private SystemHealthMonitor.HealthStatusListener healthListener = health -> {
        // Optional: Update UI with system health if needed
    };

    private void handleAccidentDetected(String severity, int confidence, float gForce, SensorLog initialLog) {
        Log.w(TAG, String.format("ACCIDENT DETECTED! Severity: %s, G-Force: %.2fG", severity, gForce));
        
        String incidentId = "INC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        
        if (sensorDataLogger != null) {
            sensorDataLogger.triggerEventCapture(incidentId, severity);
        }

        IncidentData incident = new IncidentData();
        incident.setId(incidentId);
        incident.setType("AUTO_DETECTED");
        incident.setTimestamp(System.currentTimeMillis());
        incident.setSeverity(severity);
        incident.setConfidenceScore(confidence);
        incident.setImpactForce(gForce);
        if (currentLocation != null) incident.setLocation(currentLocation);

        if (databaseHelper != null) {
            List<SensorLog> buffer = sensorDataLogger != null ? sensorDataLogger.getCircularBufferSnapshot() : new ArrayList<>();
            databaseHelper.saveSensorLogs(incidentId, buffer);
        }

        OfflineSyncManager.getInstance(this, RetrofitClient.getApiService()).saveIncidentOffline(incident);

        if ("SEVERE".equals(severity) && emergencyAlertManager != null) {
            emergencyAlertManager.sendEmergencyAlert(severity, currentLocation, incidentId);
        }

        Toast.makeText(this, "⚠️ ACCIDENT DETECTED: " + severity, Toast.LENGTH_LONG).show();
    }

    private void finalizeIncident(String incidentId) {
        IncidentData incidentData = databaseHelper.getIncidentData(incidentId);
        if (incidentData == null) return;

        android.database.Cursor cursor = databaseHelper.getSensorLogs(incidentId);
        List<SensorLog> allLogs = new ArrayList<>();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    SensorLog log = new SensorLog();
                    log.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                    log.gForceMagnitude = cursor.getFloat(cursor.getColumnIndexOrThrow("gforce_magnitude"));
                    log.speed = cursor.getFloat(cursor.getColumnIndexOrThrow("speed"));
                    log.latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    log.longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    allLogs.add(log);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        ReportGenerator reportGenerator = new ReportGenerator(this);
        File reportDir = new File(getExternalFilesDir(null), "Reports");
        if (!reportDir.exists()) reportDir.mkdirs();

        String reportPath = reportGenerator.generateReport(
                incidentData, allLogs, reportDir, "STANDARD",
                recordingStartEpochMs > 0 ? recordingStartEpochMs : null,
                recordingStopEpochMs  > 0 ? recordingStopEpochMs  : null);
        if (reportPath != null) {
            Toast.makeText(this, "Accident Report Generated", Toast.LENGTH_SHORT).show();
        }
    }

    private void markIncident() {
        String incidentId = "INC_MANUAL_" + System.currentTimeMillis();
        IncidentData incident = new IncidentData();
        incident.setId(incidentId);
        incident.setType("MANUAL");
        incident.setTimestamp(System.currentTimeMillis());
        if (currentLocation != null) incident.setLocation(currentLocation);

        OfflineSyncManager.getInstance(this, RetrofitClient.getApiService()).saveIncidentOffline(incident);
        
        Intent intent = new Intent(this, IncidentAnalysisActivity.class);
        intent.putExtra("incident_id", incidentId);
        startActivity(intent);

        Toast.makeText(this, "Manual incident marked!", Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    currentSpeed = currentLocation.getSpeed() * 3.6f; // m/s to km/h
                    if (sensorDataLogger != null) {
                        sensorDataLogger.updateLocation(currentLocation);
                    }
                    if (accidentDetector != null) {
                        accidentDetector.updateLocation(currentLocation);
                    }
                    sendLocationToBackend(currentLocation.getLatitude(), currentLocation.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void sendLocationToBackend(double lat, double lng) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("lat", lat);
        body.addProperty("lang", lng);

        RetrofitClient.getApiService().updatePhoneLocation(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<com.google.gson.JsonObject> call, @NonNull retrofit2.Response<com.google.gson.JsonObject> response) {
                if (response.isSuccessful()) {
                    Log.d("LocationSync", "Phone location successfully synced to backend: " + lat + ", " + lng);
                } else {
                    Log.e("LocationSync", "Failed to sync phone location: " + response.message());
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<com.google.gson.JsonObject> call, @NonNull Throwable t) {
                Log.e("LocationSync", "Error syncing phone location: " + t.getMessage());
            }
        });
    }

    private boolean locationPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (locationPermissionsGranted()) {
                startLocationUpdates();
            }
        }
    }

    private void startStream(String ip) {
        String streamUrl = "http://" + ip + ":81/stream";
        wvVideoStream.loadUrl(streamUrl);
        Toast.makeText(this, "Connecting to " + streamUrl, Toast.LENGTH_SHORT).show();

        // MJPEG streams (multipart/x-mixed-replace) from ESP32-CAM never complete loading,
        // so onPageFinished() may never fire. After 3 seconds, force-start detection
        // regardless — the WebView will already be displaying frames by then.
        statusHandler.postDelayed(() -> {
            if (!isStreaming && wvVideoStream.getWidth() > 0) {
                Log.d(TAG, "onPageFinished() did not fire — forcing stream active for MJPEG");
                isStreaming = true;
                pbStreamLoading.setVisibility(View.GONE);
                tvStreamStatus.setVisibility(View.GONE);
                startDetectionSimulation();
            }
        }, 3000);
    }

    private void startDetectionSimulation() {
        // Guard: prevent multiple concurrent detection loops if called from both
        // the onPageFinished callback and the MJPEG stream fallback.
        if (statusRunnable != null) {
            Log.d(TAG, "Detection simulation already running — skipping duplicate start");
            return;
        }
        statusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStreaming) {
                    captureAndDetect();
                    statusHandler.postDelayed(this, 2000); // Detect every 2 secs
                }
            }
        };
        statusHandler.post(statusRunnable);
    }

    private void captureAndDetect() {
        int wvW = wvVideoStream.getWidth();
        int wvH = wvVideoStream.getHeight();
        if (wvW <= 0 || wvH <= 0) return;
        if (detector == null) return;

        // Reuse the capture bitmap if dimensions haven't changed;
        // only reallocate when the view is first seen or resized.
        if (captureWebViewBitmap == null || captureWidth != wvW || captureHeight != wvH) {
            if (captureWebViewBitmap != null) captureWebViewBitmap.recycle();
            captureWebViewBitmap = Bitmap.createBitmap(wvW, wvH, Bitmap.Config.ARGB_8888);
            captureCanvas = new android.graphics.Canvas(captureWebViewBitmap);
            captureWidth  = wvW;
            captureHeight = wvH;
        }

        // Draw the current WebView frame into the reusable bitmap
        wvVideoStream.draw(captureCanvas);

        // Scale directly to the YOLO input size (640x640) instead of copying at full
        // resolution first. This eliminates one ~1.26MB allocation per cycle that was
        // triggering Stop-The-World GC pauses (8.7ms observed in logs).
        final Bitmap scaledFrame = Bitmap.createScaledBitmap(
                captureWebViewBitmap, YOLOv8Detector.INPUT_SIZE, YOLOv8Detector.INPUT_SIZE, true);

        detectionExecutor.execute(() -> {
            try {
                // detectPreScaled() skips the internal createScaledBitmap call since the
                // bitmap is already at the correct input resolution — saves one allocation.
                List<YOLOv8Detector.Recognition> results = detector.detectPreScaled(scaledFrame);
                runOnUiThread(() -> {
                    overlayView.setResults(results);
                    updateStatusFromDetections(results);
                });
            } catch (Exception e) {
                // Surface any silent TFLite / GPU delegate failures so they appear in logcat
                Log.e(TAG, "detectPreScaled() failed, falling back to detect()", e);
                try {
                    List<YOLOv8Detector.Recognition> results = detector.detect(scaledFrame);
                    runOnUiThread(() -> {
                        overlayView.setResults(results);
                        updateStatusFromDetections(results);
                    });
                } catch (Exception e2) {
                    Log.e(TAG, "detect() fallback also failed — model may be corrupt", e2);
                }
            } finally {
                scaledFrame.recycle();
            }
        });
    }

    private void updateStatusFromDetections(List<YOLOv8Detector.Recognition> results) {
        // Log detection count so we can verify YOLO is actually processing real frames
        if (!results.isEmpty()) {
            Log.d(TAG, "Detections: " + results.size() + " — " + results.get(0).title
                    + " (" + String.format("%.0f%%", results.get(0).confidence * 100) + ")");
        }

        if (results.isEmpty()) {
            // No objects detected at all — show clear state
            tvDistance.setText("-- cm");
            tvObstacleStatus.setText("CLEAR");
            tvObstacleStatus.setTextColor(Color.parseColor("#4CAF50"));
            ivObstacleIcon.setColorFilter(Color.parseColor("#4CAF50"));
            return;
        }

        // ── Severity tiers (all 80 COCO classes treated as obstacles) ────────────
        // HIGH: immediate collision risk
        final java.util.Set<String> HIGH = new java.util.HashSet<>(java.util.Arrays.asList(
                "person", "bicycle", "car", "motorcycle", "bus", "truck",
                "train", "boat", "traffic light", "stop sign"));
        // MEDIUM: large moving objects
        final java.util.Set<String> MEDIUM = new java.util.HashSet<>(java.util.Arrays.asList(
                "dog", "cat", "horse", "cow", "elephant", "bear", "zebra", "giraffe",
                "sheep", "bird"));
        // LOW: any other COCO object (furniture, electronics, sports equipment, etc.)

        float minDistance = 200f;
        String closestLabel = "";
        boolean isHigh = false, isMedium = false;

        for (YOLOv8Detector.Recognition res : results) {
            float boxArea = (res.location.width() * res.location.height()) / (float)(YOLOv8Detector.INPUT_SIZE * YOLOv8Detector.INPUT_SIZE);
            float dist = 200f * (1f - boxArea);
            if (dist < minDistance) {
                minDistance = dist;
                closestLabel = res.title;
            }
            if (HIGH.contains(res.title))   isHigh   = true;
            if (MEDIUM.contains(res.title)) isMedium = true;
        }

        tvDistance.setText(String.format("%.1f cm", minDistance));

        if (isHigh && minDistance < 120) {
            tvObstacleStatus.setText("OBSTACLE: " + closestLabel.toUpperCase());
            tvObstacleStatus.setTextColor(Color.parseColor("#F44336")); // Red
            ivObstacleIcon.setColorFilter(Color.parseColor("#F44336"));
        } else if ((isHigh || isMedium) && minDistance < 160) {
            tvObstacleStatus.setText("CAUTION: " + closestLabel);
            tvObstacleStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
            ivObstacleIcon.setColorFilter(Color.parseColor("#FF9800"));
        } else {
            // Any detection — show what YOLO found
            tvObstacleStatus.setText("Detected: " + closestLabel);
            tvObstacleStatus.setTextColor(Color.parseColor("#2196F3")); // Blue
            ivObstacleIcon.setColorFilter(Color.parseColor("#2196F3"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wvVideoStream.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        wvVideoStream.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop any active recording cleanly
        if (isRecording) stopRecording();
        if (servicesBound) {
            if (accidentDetector != null) accidentDetector.stopMonitoring();
            if (sensorDataLogger != null) sensorDataLogger.stopLogging();
            try {
                unbindService(accidentDetectorConnection);
                unbindService(sensorLoggerConnection);
                unbindService(emergencyAlertConnection);
                unbindService(healthMonitorConnection);
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding services", e);
            }
        }
        if (statusHandler != null && statusRunnable != null) {
            statusHandler.removeCallbacks(statusRunnable);
        }
        if (frameHandler != null && frameRunnable != null) {
            frameHandler.removeCallbacks(frameRunnable);
        }
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (detector != null) {
            detector.close();
        }
        // Shut down detection executor and release the shared capture bitmap
        detectionExecutor.shutdownNow();
        if (captureWebViewBitmap != null) {
            captureWebViewBitmap.recycle();
            captureWebViewBitmap = null;
        }
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        wvVideoStream.destroy();
        Log.d(TAG, "Activity destroyed, services cleaned up");
    }
}
