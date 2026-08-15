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
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * SensorDataLogger - Continuous sensor data capture with circular buffer
 * Features:
 * - 10-second pre-crash circular buffer
 * - 60-second post-crash data capture
 * - Time-stamped sensor records
 * - Memory-efficient storage (max 512KB in-memory)
 * - Event-wise data segmentation
 */
public class SensorDataLogger extends Service implements SensorEventListener {

    private static final String TAG = "SensorDataLogger";

    // Buffer configuration
    private static final int PRE_CRASH_DURATION_MS = 10000;  // 10 seconds
    private static final int POST_CRASH_DURATION_MS = 60000; // 60 seconds
    private static final int SAMPLING_RATE_MS = 100;         // 100ms = 10 samples/second
    private static final int MAX_BUFFER_SIZE = 100;          // 10 seconds at 10 Hz

    // Sensor components
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    // Circular buffer for pre-crash data
    private Queue<SensorLog> circularBuffer;

    // Post-crash capture state
    private String currentCaptureEventId;
    private boolean isCapturingPostCrash = false;
    private long postCrashStartTime = 0;
    private List<SensorLog> postCrashData;

    // Current sensor values
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private float[] gyroData = new float[3];

    // Current location and speed (updated externally)
    private Location currentLocation;
    private float currentSpeed = 0f;

    // Last sample time for rate limiting
    private long lastSampleTime = 0;

    // Callback listener
    private DataCaptureListener listener;

    private boolean isLogging = false;

    private final IBinder binder = new SensorDataLoggerBinder();

    public class SensorDataLoggerBinder extends Binder {
        public SensorDataLogger getService() {
            return SensorDataLogger.this;
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
        initializeComponents();
        Log.d(TAG, "SensorDataLogger service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLogging();
        return START_STICKY;
    }

    private void initializeComponents() {
        circularBuffer = new LinkedList<>();
        postCrashData = new ArrayList<>();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    public void startLogging() {
        if (isLogging) {
            Log.w(TAG, "Already logging");
            return;
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, 
                SensorManager.SENSOR_DELAY_GAME); // ~20ms delay for efficient sampling
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, 
                SensorManager.SENSOR_DELAY_GAME);
        }

        isLogging = true;
        Log.d(TAG, "Sensor data logging started");
    }

    public void stopLogging() {
        if (!isLogging) {
            return;
        }

        sensorManager.unregisterListener(this);
        isLogging = false;
        Log.d(TAG, "Sensor data logging stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();

        // Rate limiting: Only sample every SAMPLING_RATE_MS
        if (currentTime - lastSampleTime < SAMPLING_RATE_MS) {
            return;
        }
        lastSampleTime = currentTime;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometer(event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            processGyroscope(event);
        }

        // Create sensor log sample
        captureSensorSample();
    }

    private void processAccelerometer(SensorEvent event) {
        // Low-pass filter to isolate gravity
        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove gravity to get linear acceleration
        linearAcceleration[0] = event.values[0] - gravity[0];
        linearAcceleration[1] = event.values[1] - gravity[1];
        linearAcceleration[2] = event.values[2] - gravity[2];
    }

    private void processGyroscope(SensorEvent event) {
        gyroData[0] = event.values[0];
        gyroData[1] = event.values[1];
        gyroData[2] = event.values[2];
    }

    private void captureSensorSample() {
        SensorLog log = new SensorLog();
        log.timestamp = System.currentTimeMillis();

        // G-force data
        log.gForceX = linearAcceleration[0] / SensorManager.GRAVITY_EARTH;
        log.gForceY = linearAcceleration[1] / SensorManager.GRAVITY_EARTH;
        log.gForceZ = linearAcceleration[2] / SensorManager.GRAVITY_EARTH;
        log.gForceMagnitude = (float) Math.sqrt(
            log.gForceX * log.gForceX +
            log.gForceY * log.gForceY +
            log.gForceZ * log.gForceZ
        );

        // Gyroscope data
        log.gyroX = gyroData[0];
        log.gyroY = gyroData[1];
        log.gyroZ = gyroData[2];

        // Speed and location
        log.speed = currentSpeed;
        if (currentLocation != null) {
            log.latitude = currentLocation.getLatitude();
            log.longitude = currentLocation.getLongitude();
        }

        // Add to appropriate buffer
        if (isCapturingPostCrash) {
            addToPostCrashBuffer(log);
        } else {
            addToCircularBuffer(log);
        }
    }

    /**
     * Add sample to circular buffer (pre-crash data)
     * Maintains only last 10 seconds of data
     */
    private void addToCircularBuffer(SensorLog log) {
        circularBuffer.add(log);

        // Remove old samples to maintain buffer size
        while (circularBuffer.size() > MAX_BUFFER_SIZE) {
            circularBuffer.poll();
        }
    }

    /**
     * Add sample to post-crash buffer
     * Captures 60 seconds after accident
     */
    private void addToPostCrashBuffer(SensorLog log) {
        postCrashData.add(log);

        // Check if post-crash capture period is complete
        long elapsedTime = System.currentTimeMillis() - postCrashStartTime;
        if (elapsedTime >= POST_CRASH_DURATION_MS) {
            finalizeEventCapture();
        }
    }

    /**
     * Trigger event capture on accident detection
     * Saves circular buffer (pre-crash) and starts post-crash capture
     */
    public void triggerEventCapture(String eventId, String severity) {
        Log.i(TAG, "Event capture triggered: " + eventId + " (" + severity + ")");
        this.currentCaptureEventId = eventId;

        // Save pre-crash data (circular buffer)
        List<SensorLog> preCrashData = new ArrayList<>(circularBuffer);

        // Start post-crash capture
        isCapturingPostCrash = true;
        postCrashStartTime = System.currentTimeMillis();
        postCrashData.clear();

        Log.d(TAG, String.format(
            "Captured %d pre-crash samples, starting post-crash capture...",
            preCrashData.size()
        ));

        // Notify listener about pre-crash data immediately
        if (listener != null) {
            listener.onPreCrashDataCaptured(eventId, preCrashData);
        }
    }

    /**
     * Finalize event capture after 60 seconds
     */
    private void finalizeEventCapture() {
        isCapturingPostCrash = false;

        Log.d(TAG, String.format(
            "Event capture finalized. Post-crash samples: %d",
            postCrashData.size()
        ));

        // Notify listener with post-crash data
        if (listener != null) {
            List<SensorLog> postCrashCopy = new ArrayList<>(postCrashData);
            listener.onPostCrashDataCaptured(currentCaptureEventId, postCrashCopy);
        }

        postCrashData.clear();
    }

    /**
     * Get current circular buffer (for debugging/monitoring)
     */
    public List<SensorLog> getCircularBufferSnapshot() {
        return new ArrayList<>(circularBuffer);
    }

    /**
     * Get memory usage estimate
     */
    public long getEstimatedMemoryUsage() {
        // Approximate size: each SensorLog ~80 bytes
        int totalSamples = circularBuffer.size() + postCrashData.size();
        return totalSamples * 80L; // bytes
    }

    /**
     * Update location and speed (called externally)
     */
    public void updateLocation(Location location) {
        this.currentLocation = location;
        if (location != null && location.hasSpeed()) {
            this.currentSpeed = location.getSpeed() * 3.6f; // m/s to km/h
        }
    }

    public void setDataCaptureListener(DataCaptureListener listener) {
        this.listener = listener;
    }

    public boolean isLogging() {
        return isLogging;
    }

    public boolean isCapturingPostCrash() {
        return isCapturingPostCrash;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLogging();
        circularBuffer.clear();
        postCrashData.clear();
        Log.d(TAG, "SensorDataLogger service destroyed");
    }

    /**
     * Listener interface for data capture callbacks
     */
    public interface DataCaptureListener {
        void onPreCrashDataCaptured(String eventId, List<SensorLog> preCrashData);
        void onPostCrashDataCaptured(String eventId, List<SensorLog> postCrashData);
    }
}
