package com.example.smartfleetx.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.smartfleetx.R;
import com.example.smartfleetx.service.SystemHealthMonitor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.Toast;
import com.example.smartfleetx.database.DatabaseHelper;
import com.example.smartfleetx.service.ReportGenerator;
import java.io.File;

/**
 * SystemHealthActivity - Real-time system health monitoring
 * Features:
 * - Live health score display
 * - Individual component status (Sensors, Network, Battery, Storage)
 * - Historical uptime tracking
 * - Diagnostic information
 * - Health trend visualization
 */
public class SystemHealthActivity extends AppCompatActivity {

    private static final String TAG = "SystemHealthActivity";

    // UI Components
    private TextView tvOverallScore, tvHealthStatus, tvLastUpdate, tvUptime;
    private ProgressBar pbOverallHealth;
    
    // Sensor Status
    private CardView cardSensors;
    private TextView tvSensorStatus, tvSensorDetails, tvSensorScore;
    private ProgressBar pbSensors;
    
    // Network Status
    private CardView cardNetwork;
    private TextView tvNetworkStatus, tvNetworkDetails, tvNetworkScore;
    private ProgressBar pbNetwork;
    
    // Battery Status
    private CardView cardBattery;
    private TextView tvBatteryStatus, tvBatteryDetails, tvBatteryScore;
    private ProgressBar pbBattery;
    
    // Storage Status
    private CardView cardStorage;
    private TextView tvStorageStatus, tvStorageDetails, tvStorageScore;
    private ProgressBar pbStorage;
    
    // Diagnostic Section
    private TextView tvDiagnosticInfo, tvHeartbeatStatus;
    private Button btnRefresh, btnRunDiagnostics, btnExportReport, btnBack;

    // Service
    private SystemHealthMonitor healthMonitor;
    private boolean isServiceBound = false;
    
    // Auto-refresh handler
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    private ServiceConnection healthServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SystemHealthMonitor.SystemHealthBinder binder = 
                (SystemHealthMonitor.SystemHealthBinder) service;
            healthMonitor = binder.getService();
            healthMonitor.setHealthStatusListener(healthStatusListener);
            isServiceBound = true;
            
            // Get initial health status
            updateHealthDisplay(healthMonitor.getCurrentHealth());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            healthMonitor = null;
            isServiceBound = false;
        }
    };

    private SystemHealthMonitor.HealthStatusListener healthStatusListener = 
        new SystemHealthMonitor.HealthStatusListener() {
        @Override
        public void onHealthUpdated(SystemHealthMonitor.SystemHealth health) {
            runOnUiThread(() -> updateHealthDisplay(health));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_health);

        initializeViews();
        setupListeners();
        bindHealthService();
        setupAutoRefresh();
    }

    private void initializeViews() {
        // Overall Health
        tvOverallScore = findViewById(R.id.tvOverallScore);
        tvHealthStatus = findViewById(R.id.tvHealthStatus);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvUptime = findViewById(R.id.tvUptime);
        pbOverallHealth = findViewById(R.id.pbOverallHealth);
        
        // Sensors
        cardSensors = findViewById(R.id.cardSensors);
        tvSensorStatus = findViewById(R.id.tvSensorStatus);
        tvSensorDetails = findViewById(R.id.tvSensorDetails);
        tvSensorScore = findViewById(R.id.tvSensorScore);
        pbSensors = findViewById(R.id.pbSensors);
        
        // Network
        cardNetwork = findViewById(R.id.cardNetwork);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        tvNetworkDetails = findViewById(R.id.tvNetworkDetails);
        tvNetworkScore = findViewById(R.id.tvNetworkScore);
        pbNetwork = findViewById(R.id.pbNetwork);
        
        // Battery
        cardBattery = findViewById(R.id.cardBattery);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvBatteryDetails = findViewById(R.id.tvBatteryDetails);
        tvBatteryScore = findViewById(R.id.tvBatteryScore);
        pbBattery = findViewById(R.id.pbBattery);
        
        // Storage
        cardStorage = findViewById(R.id.cardStorage);
        tvStorageStatus = findViewById(R.id.tvStorageStatus);
        tvStorageDetails = findViewById(R.id.tvStorageDetails);
        tvStorageScore = findViewById(R.id.tvStorageScore);
        pbStorage = findViewById(R.id.pbStorage);
        
        // Diagnostics
        tvDiagnosticInfo = findViewById(R.id.tvDiagnosticInfo);
        tvHeartbeatStatus = findViewById(R.id.tvHeartbeatStatus);
        
        // Buttons
        btnRefresh = findViewById(R.id.btnRefresh);
        btnRunDiagnostics = findViewById(R.id.btnRunDiagnostics);
        btnExportReport = findViewById(R.id.btnExportReport);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> refreshHealth());
        btnRunDiagnostics.setOnClickListener(v -> runDiagnostics());
        btnExportReport.setOnClickListener(v -> exportHealthReport());
        btnBack.setOnClickListener(v -> finish());
    }

    private void bindHealthService() {
        Intent intent = new Intent(this, SystemHealthMonitor.class);
        bindService(intent, healthServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceBound && healthMonitor != null) {
                    updateHealthDisplay(healthMonitor.getCurrentHealth());
                }
                refreshHandler.postDelayed(this, 5000); // Refresh every 5 seconds
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void refreshHealth() {
        if (isServiceBound && healthMonitor != null) {
            healthMonitor.performHealthCheck();
            updateHealthDisplay(healthMonitor.getCurrentHealth());
        }
    }

    private void runDiagnostics() {
        if (isServiceBound && healthMonitor != null) {
            SystemHealthMonitor.SystemHealth health = healthMonitor.getCurrentHealth();
            
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("=== SYSTEM DIAGNOSTICS ===\n\n");
            
            diagnostics.append("Overall Health: ").append(health.status).append("\n");
            diagnostics.append("Score: ").append(String.format(Locale.getDefault(), "%.1f%%", health.overallScore * 100)).append("\n\n");
            
            diagnostics.append("--- SENSORS ---\n");
            diagnostics.append("Available: ").append(health.sensorsAvailable).append("/2\n");
            diagnostics.append("Score: ").append(String.format(Locale.getDefault(), "%.0f%%", health.sensorScore * 100)).append("\n\n");
            
            diagnostics.append("--- NETWORK ---\n");
            diagnostics.append("Connected: ").append(health.networkConnected ? "YES" : "NO").append("\n");
            diagnostics.append("Internet: ").append(health.hasInternet ? "YES" : "NO").append("\n");
            diagnostics.append("Type: ").append(health.networkType).append("\n");
            diagnostics.append("Score: ").append(String.format(Locale.getDefault(), "%.0f%%", health.networkScore * 100)).append("\n\n");
            
            diagnostics.append("--- BATTERY ---\n");
            diagnostics.append("Level: ").append(health.batteryLevel).append("%\n");
            diagnostics.append("Charging: ").append(health.isCharging ? "YES" : "NO").append("\n");
            diagnostics.append("Score: ").append(String.format(Locale.getDefault(), "%.0f%%", health.batteryScore * 100)).append("\n\n");
            
            diagnostics.append("--- STORAGE ---\n");
            diagnostics.append("Free: ").append(health.freeStorageMB).append(" MB\n");
            diagnostics.append("Total: ").append(health.totalStorageMB).append(" MB\n");
            diagnostics.append("Score: ").append(String.format(Locale.getDefault(), "%.0f%%", health.storageScore * 100)).append("\n\n");
            
            diagnostics.append("Last Check: ").append(
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(health.lastCheckTime))
            ).append("\n");
            
            tvDiagnosticInfo.setText(diagnostics.toString());
        }
    }

    private void exportHealthReport() {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            // Get logs from last 7 days
            long sinceTimestamp = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            android.database.Cursor cursor = dbHelper.getHealthLogs(sinceTimestamp);
            
            ReportGenerator reportGenerator = new ReportGenerator(this);
            File reportDir = new File(getExternalFilesDir(null), "Reports");
            if (!reportDir.exists()) reportDir.mkdirs();
            
            String reportPath = reportGenerator.generateSystemHealthReport(cursor, reportDir);
            
            if (cursor != null) cursor.close();
            dbHelper.close();
            
            if (reportPath != null) {
                Toast.makeText(this, "Report saved to: " + reportPath, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to generate report", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHealthDisplay(SystemHealthMonitor.SystemHealth health) {
        // Overall Health
        int overallPercent = (int) (health.overallScore * 100);
        tvOverallScore.setText(String.format(Locale.getDefault(), "%d%%", overallPercent));
        pbOverallHealth.setProgress(overallPercent);
        
        // Status with emoji
        String statusEmoji = getStatusEmoji(health.status);
        tvHealthStatus.setText(statusEmoji + " " + health.status);
        tvHealthStatus.setTextColor(getStatusColor(health.status));
        
        // Last update
        String lastUpdate = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(new Date(health.lastCheckTime));
        tvLastUpdate.setText("Last Update: " + lastUpdate);
        
        // Uptime
        long uptimeMs = health.uptimeMs;
        long hours = uptimeMs / (1000 * 60 * 60);
        long minutes = (uptimeMs / (1000 * 60)) % 60;
        tvUptime.setText(String.format(Locale.getDefault(), "Uptime: %dh %dm", hours, minutes));
        
        // Update individual components
        updateSensorCard(health);
        updateNetworkCard(health);
        updateBatteryCard(health);
        updateStorageCard(health);
        
        // Heartbeat status
        tvHeartbeatStatus.setText(String.format(Locale.getDefault(), 
            "💓 Heartbeat: Active (Last: %s)", lastUpdate));
    }

    private void updateSensorCard(SystemHealthMonitor.SystemHealth health) {
        int sensorPercent = (int) (health.sensorScore * 100);
        tvSensorScore.setText(String.format(Locale.getDefault(), "%d%%", sensorPercent));
        pbSensors.setProgress(sensorPercent);
        
        String status = health.sensorsAvailable == 2 ? "✅ All Sensors Active" : 
                       health.sensorsAvailable == 1 ? "⚠️ Partial" : "❌ Unavailable";
        tvSensorStatus.setText(status);
        
        tvSensorDetails.setText(String.format(Locale.getDefault(), 
            "Available: %d/2\nAccelerometer: %s\nGyroscope: %s",
            health.sensorsAvailable,
            health.sensorsAvailable >= 1 ? "OK" : "Missing",
            health.sensorsAvailable == 2 ? "OK" : "Missing"));
        
        cardSensors.setCardBackgroundColor(getScoreBackgroundColor(health.sensorScore));
    }

    private void updateNetworkCard(SystemHealthMonitor.SystemHealth health) {
        int networkPercent = (int) (health.networkScore * 100);
        tvNetworkScore.setText(String.format(Locale.getDefault(), "%d%%", networkPercent));
        pbNetwork.setProgress(networkPercent);
        
        String status = health.networkConnected && health.hasInternet ? "✅ Connected" :
                       health.networkConnected ? "⚠️ No Internet" : "❌ Disconnected";
        tvNetworkStatus.setText(status);
        
        tvNetworkDetails.setText(String.format(Locale.getDefault(),
            "Type: %s\nInternet: %s\nConnection: %s",
            health.networkType,
            health.hasInternet ? "Available" : "Unavailable",
            health.networkConnected ? "Active" : "Inactive"));
        
        cardNetwork.setCardBackgroundColor(getScoreBackgroundColor(health.networkScore));
    }

    private void updateBatteryCard(SystemHealthMonitor.SystemHealth health) {
        int batteryPercent = (int) (health.batteryScore * 100);
        tvBatteryScore.setText(String.format(Locale.getDefault(), "%d%%", batteryPercent));
        pbBattery.setProgress(batteryPercent);
        
        String status = health.batteryLevel > 50 ? "✅ Good" :
                       health.batteryLevel > 20 ? "⚠️ Fair" : "❌ Low";
        tvBatteryStatus.setText(status);
        
        tvBatteryDetails.setText(String.format(Locale.getDefault(),
            "Level: %d%%\nCharging: %s\nStatus: %s",
            health.batteryLevel,
            health.isCharging ? "Yes" : "No",
            health.batteryLevel > 50 ? "Optimal" : health.batteryLevel > 20 ? "Adequate" : "Critical"));
        
        cardBattery.setCardBackgroundColor(getScoreBackgroundColor(health.batteryScore));
    }

    private void updateStorageCard(SystemHealthMonitor.SystemHealth health) {
        int storagePercent = (int) (health.storageScore * 100);
        tvStorageScore.setText(String.format(Locale.getDefault(), "%d%%", storagePercent));
        pbStorage.setProgress(storagePercent);
        
        double freeGB = health.freeStorageMB / 1024.0;
        String status = freeGB > 1.0 ? "✅ Available" :
                       freeGB > 0.5 ? "⚠️ Low" : "❌ Critical";
        tvStorageStatus.setText(status);
        
        tvStorageDetails.setText(String.format(Locale.getDefault(),
            "Free: %.2f GB\nUsed: %.2f GB\nTotal: %.2f GB",
            freeGB,
            (health.totalStorageMB - health.freeStorageMB) / 1024.0,
            health.totalStorageMB / 1024.0));
        
        cardStorage.setCardBackgroundColor(getScoreBackgroundColor(health.storageScore));
    }

    private String getStatusEmoji(String status) {
        switch (status) {
            case "HEALTHY":
                return "✅";
            case "WARNING":
                return "⚠️";
            case "CRITICAL":
                return "❌";
            default:
                return "❓";
        }
    }

    private int getStatusColor(String status) {
        switch (status) {
            case "HEALTHY":
                return Color.parseColor("#4CAF50"); // Green
            case "WARNING":
                return Color.parseColor("#FF9800"); // Orange
            case "CRITICAL":
                return Color.parseColor("#F44336"); // Red
            default:
                return Color.GRAY;
        }
    }

    private int getScoreBackgroundColor(double score) {
        if (score >= 0.9) {
            return Color.parseColor("#E8F5E9"); // Light green
        } else if (score >= 0.7) {
            return Color.parseColor("#FFF3E0"); // Light orange
        } else {
            return Color.parseColor("#FFEBEE"); // Light red
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        
        if (isServiceBound) {
            try {
                unbindService(healthServiceConnection);
            } catch (IllegalArgumentException e) {
                // Service not registered
            }
        }
    }
}
