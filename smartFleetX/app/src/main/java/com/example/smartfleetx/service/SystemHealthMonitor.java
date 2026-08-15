package com.example.smartfleetx.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SystemHealthMonitor - Monitors system health and reliability
 * Features:
 * - Sensor availability monitoring
 * - Network connectivity status
 * - Battery level tracking
 * - Storage space monitoring
 * - Component health scoring
 */
public class SystemHealthMonitor extends Service {

    private static final String TAG = "SystemHealthMonitor";

    // Monitoring intervals
    private static final long HEALTH_CHECK_INTERVAL = 60000; // 1 minute
    private static final long HEARTBEAT_INTERVAL = 300000;    // 5 minutes

    private SensorManager sensorManager;
    private ConnectivityManager connectivityManager;
    private BatteryManager batteryManager;
    private Handler healthCheckHandler;
    private Handler heartbeatHandler;
    private com.example.smartfleetx.database.DatabaseHelper databaseHelper;

    // Health status
    private SystemHealth currentHealth;
    private HealthStatusListener healthListener;
    private long lastHeartbeat;
    private int healthCheckCount = 0;

    private final IBinder binder = new SystemHealthBinder();

    public class SystemHealthBinder extends Binder {
        public SystemHealthMonitor getService() {
            return SystemHealthMonitor.this;
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
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        
        healthCheckHandler = new Handler(Looper.getMainLooper());
        heartbeatHandler = new Handler(Looper.getMainLooper());
        
        databaseHelper = new com.example.smartfleetx.database.DatabaseHelper(this);
        currentHealth = new SystemHealth();
        
        startMonitoring();
        
        Log.d(TAG, "SystemHealthMonitor service created");
    }

    /**
     * Start health monitoring
     */
    private void startMonitoring() {
        // Initial health check
        performHealthCheck();
        
        // Schedule periodic health checks
        healthCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performHealthCheck();
                healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL);
            }
        }, HEALTH_CHECK_INTERVAL);

        // Schedule heartbeat
        heartbeatHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        }, HEARTBEAT_INTERVAL);
    }

    /**
     * Perform comprehensive health check
     */
    public void performHealthCheck() {
        healthCheckCount++;
        
        Log.d(TAG, "Performing health check #" + healthCheckCount);

        // Check sensors
        checkSensorHealth();
        
        // Check network
        checkNetworkHealth();
        
        // Check battery
        checkBatteryHealth();
        
        // Check storage
        checkStorageHealth();
        
        // Calculate overall health score
        float healthScore = calculateHealthScore();
        currentHealth.overallScore = healthScore;
        currentHealth.lastCheckTime = System.currentTimeMillis();
        currentHealth.uptimeMs = getUptimeMs();
        
        // Determine status
        if (healthScore >= 0.9f) {
            currentHealth.status = "HEALTHY";
        } else if (healthScore >= 0.7f) {
            currentHealth.status = "WARNING";
        } else {
            currentHealth.status = "CRITICAL";
        }

        Log.i(TAG, String.format("Health check complete: %s (score: %.2f)", 
            currentHealth.status, healthScore));

        // Notify listener
        notifyHealthUpdate();
    }

    /**
     * Check sensor availability and functionality
     */
    private void checkSensorHealth() {
        List<String> availableSensors = new ArrayList<>();
        List<String> missingSensors = new ArrayList<>();

        // Check accelerometer
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            availableSensors.add("Accelerometer");
        } else {
            missingSensors.add("Accelerometer");
            Log.w(TAG, "Accelerometer not available");
        }

        // Check gyroscope
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope != null) {
            availableSensors.add("Gyroscope");
        } else {
            missingSensors.add("Gyroscope");
            Log.w(TAG, "Gyroscope not available");
        }

        // Check GPS (via location manager)
        // This would need LocationManager check

        currentHealth.sensorsAvailable = availableSensors.size();
        currentHealth.sensorsRequired = 2; // Accelerometer + Gyroscope
        currentHealth.sensorStatus = missingSensors.isEmpty() ? "OK" : 
            "Missing: " + String.join(", ", missingSensors);
    }

    /**
     * Check network connectivity
     */
    private void checkNetworkHealth() {
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            
            if (network != null) {
                NetworkCapabilities capabilities = 
                    connectivityManager.getNetworkCapabilities(network);
                
                if (capabilities != null) {
                    boolean hasInternet = capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    boolean isConnected = capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                    currentHealth.networkStatus = isConnected ? "CONNECTED" : "DISCONNECTED";
                    currentHealth.networkConnected = isConnected;
                    currentHealth.hasInternet = hasInternet;

                    // Determine connection type
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        currentHealth.connectionType = "WiFi";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        currentHealth.connectionType = "Cellular";
                    } else {
                        currentHealth.connectionType = "Other";
                    }
                    currentHealth.networkType = currentHealth.connectionType;
                } else {
                    currentHealth.networkStatus = "UNAVAILABLE";
                    currentHealth.networkConnected = false;
                    currentHealth.hasInternet = false;
                    currentHealth.networkType = "UNKNOWN";
                    currentHealth.connectionType = "UNKNOWN";
                }
            } else {
                currentHealth.networkStatus = "DISCONNECTED";
                currentHealth.networkConnected = false;
                currentHealth.hasInternet = false;
                currentHealth.networkType = "UNKNOWN";
                currentHealth.connectionType = "UNKNOWN";
            }
        }
    }

    /**
     * Check battery status
     */
    private void checkBatteryHealth() {
        if (batteryManager != null) {
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            currentHealth.batteryLevel = batteryLevel;

            // Check if charging
            int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            currentHealth.isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                       status == BatteryManager.BATTERY_STATUS_FULL);

            // Battery status
            if (batteryLevel < 15) {
                currentHealth.batteryStatus = "CRITICAL";
                Log.w(TAG, "Battery level critical: " + batteryLevel + "%");
            } else if (batteryLevel < 30) {
                currentHealth.batteryStatus = "LOW";
            } else {
                currentHealth.batteryStatus = "OK";
            }
        }
    }

    /**
     * Check storage space
     */
    private void checkStorageHealth() {
        try {
            java.io.File dataDir = getFilesDir();
            long freeSpace = dataDir.getFreeSpace();
            long totalSpace = dataDir.getTotalSpace();
            long usedSpace = totalSpace - freeSpace;

            currentHealth.freeStorageMB = freeSpace / (1024 * 1024);
            currentHealth.usedSpaceMB = usedSpace / (1024 * 1024);
            currentHealth.totalStorageMB = totalSpace / (1024 * 1024);

            // Storage status
            float usagePercent = (float) usedSpace / totalSpace * 100;
            if (usagePercent > 90) {
                currentHealth.storageStatus = "CRITICAL";
                Log.w(TAG, "Storage critical: " + usagePercent + "% used");
            } else if (usagePercent > 75) {
                currentHealth.storageStatus = "WARNING";
            } else {
                currentHealth.storageStatus = "OK";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage", e);
            currentHealth.storageStatus = "ERROR";
        }
    }

    /**
     * Calculate overall health score (0-1)
     */
    private float calculateHealthScore() {
        float totalScore = 0.0f;

        // Sensor Score (30%)
        currentHealth.sensorScore = (currentHealth.sensorsAvailable == currentHealth.sensorsRequired) ? 1.0f :
            ((float) currentHealth.sensorsAvailable / currentHealth.sensorsRequired);
        totalScore += 0.30f * currentHealth.sensorScore;

        // Network Score (25%)
        float netScore = 0.0f;
        if (currentHealth.hasInternet && "CONNECTED".equals(currentHealth.networkStatus)) {
            netScore = 1.0f;
        } else if ("CONNECTED".equals(currentHealth.networkStatus)) {
            netScore = 0.6f; // Connected but no internet
        }
        currentHealth.networkScore = netScore;
        totalScore += 0.25f * netScore;

        // Battery Score (20%)
        float battScore = 0.0f;
        if (currentHealth.batteryLevel >= 30) {
            battScore = 1.0f;
        } else if (currentHealth.batteryLevel >= 15) {
            battScore = 0.5f;
        }
        currentHealth.batteryScore = battScore;
        totalScore += 0.20f * battScore;

        // Storage Score (25%)
        float storeScore = 0.0f;
        float storageUsagePercent = (float) currentHealth.usedSpaceMB / currentHealth.totalStorageMB * 100;
        if (storageUsagePercent < 75) {
            storeScore = 1.0f;
        } else if (storageUsagePercent < 90) {
            storeScore = 0.6f;
        }
        currentHealth.storageScore = storeScore;
        totalScore += 0.25f * storeScore;

        return Math.max(0.0f, Math.min(1.0f, totalScore));
    }

    /**
     * Send heartbeat (system uptime check)
     */
    private void sendHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
        
        Log.d(TAG, "Heartbeat sent - System operational");
        
        // Save health log to database
        if (databaseHelper != null) {
            databaseHelper.saveHealthLog(
                lastHeartbeat,
                currentHealth.status,
                currentHealth.overallScore,
                currentHealth.uptimeMs,
                currentHealth.batteryLevel,
                currentHealth.freeStorageMB,
                currentHealth.networkStatus
            );
            Log.d(TAG, "Health log saved to database");
        }
        
        // Could send to backend API for remote monitoring
        // apiService.sendHeartbeat(currentHealth);
    }

    /**
     * Get current system health
     */
    public SystemHealth getCurrentHealth() {
        return currentHealth;
    }

    /**
     * Get uptime since last heartbeat
     */
    public long getUptimeMs() {
        return System.currentTimeMillis() - lastHeartbeat;
    }

    public void setHealthStatusListener(HealthStatusListener listener) {
        this.healthListener = listener;
    }

    private void notifyHealthUpdate() {
        if (healthListener != null) {
            healthListener.onHealthUpdated(currentHealth);
        }
    }

    /**
     * System Health data class
     */
    public static class SystemHealth {
        public String status = "UNKNOWN";              // HEALTHY, WARNING, CRITICAL
        public float overallScore = 0.0f;              // 0-1
        
        // Component Scores
        public float sensorScore = 0.0f;
        public float networkScore = 0.0f;
        public float batteryScore = 0.0f;
        public float storageScore = 0.0f;
        public long uptimeMs = 0;

        // Sensors
        public int sensorsAvailable = 0;
        public int sensorsRequired = 0;
        public String sensorStatus = "UNKNOWN";

        // Network
        public String networkStatus = "UNKNOWN";       // CONNECTED, DISCONNECTED
        public boolean hasInternet = false;
        public boolean networkConnected = false;
        public String connectionType = "UNKNOWN";      // WiFi, Cellular
        public String networkType = "UNKNOWN";

        // Battery
        public int batteryLevel = 0;           // 0-100
        public boolean isCharging = false;
        public String batteryStatus = "UNKNOWN";       // OK, LOW, CRITICAL

        // Storage
        public long freeStorageMB = 0;
        public long totalStorageMB = 0;
        public long usedSpaceMB = 0;
        public String storageStatus = "UNKNOWN";       // OK, WARNING, CRITICAL

        // Metadata
        public long lastCheckTime = 0;

        @Override
        public String toString() {
            return String.format("SystemHealth{status=%s, score=%.2f, sensors=%d/%d, network=%s, battery=%d%%, storage=%dMB}",
                status, overallScore, sensorsAvailable, sensorsRequired, networkStatus, 
                batteryLevel, freeStorageMB);
        }
    }

    /**
     * Health status listener interface
     */
    public interface HealthStatusListener {
        void onHealthUpdated(SystemHealth health);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        healthCheckHandler.removeCallbacksAndMessages(null);
        heartbeatHandler.removeCallbacksAndMessages(null);
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Log.d(TAG, "SystemHealthMonitor service destroyed");
    }
}
