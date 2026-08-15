package com.example.smartfleetx.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.smartfleetx.model.SensorLog;
import com.example.smartfleetx.utils.AccidentConfidenceScorer;

import java.util.ArrayList;
import java.util.List;

/**
 * AccidentDetector Service - Continuous real-time accident detection
 * Features:
 * - Multi-sensor fusion (accelerometer + gyroscope)
 * - Configurable G-force thresholds
 * - Impact pattern recognition
 * - False positive filtering
 * - Low-latency event triggering (<100ms)
 */
public class AccidentDetector extends Service implements SensorEventListener {

    private static final String TAG = "AccidentDetector";

    // Configurable thresholds (in G-force)
    private static final float THRESHOLD_MINOR = 4.0f;      // 4G
    private static final float THRESHOLD_MODERATE = 6.0f;   // 6G
    private static final float THRESHOLD_SEVERE = 8.0f;     // 8G

    // Sensor sampling rate (microseconds)
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST;

    // False positive filtering
    private static final long MIN_TIME_BETWEEN_EVENTS = 5000; // 5 seconds
    private static final int SPIKE_CONFIRMATION_WINDOW = 100; // 100ms

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    // Sensor data buffers
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private float[] gyroData = new float[3];

    // Detection state
    private long lastEventTime = 0;
    private boolean isMonitoring = false;
    private AccidentDetectionListener listener;

    // Circular buffer for spike confirmation
    private List<Float> recentGForces = new ArrayList<>();
    private static final int BUFFER_SIZE = 10;

    // Current location (updated externally)
    private Location currentLocation;
    private float currentSpeed = 0f;

    private final IBinder binder = new AccidentDetectorBinder();

    public class AccidentDetectorBinder extends Binder {
        public AccidentDetector getService() {
            return AccidentDetector.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeSensors();
        Log.d(TAG, "AccidentDetector service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startMonitoring();
        return START_STICKY;
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            if (accelerometer == null) {
                Log.e(TAG, "Accelerometer not available!");
            }
            if (gyroscope == null) {
                Log.w(TAG, "Gyroscope not available - using accelerometer only");
            }
        }
    }

    public void startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring");
            return;
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SENSOR_DELAY);
        }

        isMonitoring = true;
        Log.d(TAG, "Accident monitoring started");
    }

    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }

        sensorManager.unregisterListener(this);
        isMonitoring = false;
        Log.d(TAG, "Accident monitoring stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometerData(event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            processGyroscopeData(event);
        }
    }

    private void processAccelerometerData(SensorEvent event) {
        // Apply low-pass filter to isolate gravity
        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Apply simple low-pass filter to linear acceleration to reduce noise
        linearAcceleration[0] = 0.9f * linearAcceleration[0] + 0.1f * (event.values[0] - gravity[0]);
        linearAcceleration[1] = 0.9f * linearAcceleration[1] + 0.1f * (event.values[1] - gravity[1]);
        linearAcceleration[2] = 0.9f * linearAcceleration[2] + 0.1f * (event.values[2] - gravity[2]);

        // Calculate G-force magnitude
        float gForce = calculateGForceMagnitude(linearAcceleration);

        // Add to circular buffer for spike confirmation
        addToBuffer(gForce);

        // Check for accident
        checkForAccident(gForce, event.timestamp);
    }

    private void processGyroscopeData(SensorEvent event) {
        // Store gyroscope data for impact pattern analysis
        gyroData[0] = event.values[0];
        gyroData[1] = event.values[1];
        gyroData[2] = event.values[2];
    }

    private float calculateGForceMagnitude(float[] acceleration) {
        float x = acceleration[0] / SensorManager.GRAVITY_EARTH;
        float y = acceleration[1] / SensorManager.GRAVITY_EARTH;
        float z = acceleration[2] / SensorManager.GRAVITY_EARTH;

        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private void addToBuffer(float gForce) {
        recentGForces.add(gForce);
        if (recentGForces.size() > BUFFER_SIZE) {
            recentGForces.remove(0);
        }
    }

    private void checkForAccident(float gForce, long timestamp) {
        // Check cooldown period to avoid duplicate detections
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEventTime < MIN_TIME_BETWEEN_EVENTS) {
            return;
        }

        // Check if G-force exceeds threshold
        if (gForce >= THRESHOLD_MINOR) {
            // Confirm spike is not a false positive
            if (isValidImpact(gForce)) {
                handleAccidentDetection(gForce, timestamp);
            } else {
                Log.d(TAG, "Impact filtered as false positive (speed bump/pothole)");
            }
        }
    }

    /**
     * False positive filtering:
     * - Check if spike is sustained (not just a single sample)
     * - Check impact pattern (sudden vs gradual)
     * - Filter speed bumps and potholes
     */
    private boolean isValidImpact(float gForce) {
        if (recentGForces.size() < 3) {
            return true; // Not enough data, allow it
        }

        // Count how many recent samples are above threshold
        int spikeCount = 0;
        for (float g : recentGForces) {
            if (g >= THRESHOLD_MINOR * 0.7f) { // 70% of threshold
                spikeCount++;
            }
        }

        // Valid impact should have sustained spike (at least 30% of buffer)
        // Single sample spikes are likely false positives
        boolean isSustained = spikeCount >= (recentGForces.size() * 0.3);

        // Check gyroscope for rotational movement (indicates impact)
        float gyroMagnitude = (float) Math.sqrt(
            gyroData[0] * gyroData[0] +
            gyroData[1] * gyroData[1] +
            gyroData[2] * gyroData[2]
        );

        // Valid impacts typically have significant rotation
        boolean hasRotation = gyroMagnitude > 0.5f || gyroscope == null; // Allow if no gyro

        return isSustained && hasRotation;
    }

    private void handleAccidentDetection(float gForce, long timestamp) {
        lastEventTime = System.currentTimeMillis();

        // Determine severity
        String severity = classifySeverity(gForce);

        // Calculate confidence score
        AccidentConfidenceScorer scorer = new AccidentConfidenceScorer();
        int confidenceScore = scorer.calculateConfidence(
            gForce,
            gyroData,
            currentSpeed,
            recentGForces
        );

        Log.i(TAG, String.format(
            "ACCIDENT DETECTED! G-Force: %.2f, Severity: %s, Confidence: %d%%",
            gForce, severity, confidenceScore
        ));

        // Create sensor log snapshot
        SensorLog sensorLog = new SensorLog();
        sensorLog.timestamp = System.currentTimeMillis();
        sensorLog.gForceX = linearAcceleration[0] / SensorManager.GRAVITY_EARTH;
        sensorLog.gForceY = linearAcceleration[1] / SensorManager.GRAVITY_EARTH;
        sensorLog.gForceZ = linearAcceleration[2] / SensorManager.GRAVITY_EARTH;
        sensorLog.gForceMagnitude = gForce;
        sensorLog.gyroX = gyroData[0];
        sensorLog.gyroY = gyroData[1];
        sensorLog.gyroZ = gyroData[2];
        sensorLog.speed = currentSpeed;
        
        if (currentLocation != null) {
            sensorLog.latitude = currentLocation.getLatitude();
            sensorLog.longitude = currentLocation.getLongitude();
        }

        // Notify listener
        if (listener != null) {
            listener.onAccidentDetected(severity, confidenceScore, gForce, sensorLog);
        }
    }

    private String classifySeverity(float gForce) {
        if (gForce >= THRESHOLD_SEVERE) {
            return "SEVERE";
        } else if (gForce >= THRESHOLD_MODERATE) {
            return "MODERATE";
        } else {
            return "MINOR";
        }
    }

    // Public methods for external updates
    public void updateLocation(Location location) {
        this.currentLocation = location;
        if (location.hasSpeed()) {
            this.currentSpeed = location.getSpeed() * 3.6f; // m/s to km/h
        }
    }

    public void setAccidentDetectionListener(AccidentDetectionListener listener) {
        this.listener = listener;
    }

    public boolean isMonitoring() {
        return isMonitoring;
    }

    public float getCurrentGForce() {
        if (recentGForces.isEmpty()) {
            return 0f;
        }
        return recentGForces.get(recentGForces.size() - 1);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        Log.d(TAG, "AccidentDetector service destroyed");
    }

    /**
     * Listener interface for accident detection callbacks
     */
    public interface AccidentDetectionListener {
        void onAccidentDetected(String severity, int confidence, float gForce, SensorLog sensorLog);
    }
}
