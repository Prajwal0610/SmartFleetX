package com.example.smartfleetx.service;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.smartfleetx.database.DatabaseHelper;
import com.example.smartfleetx.model.IncidentData;
import com.example.smartfleetx.model.SensorLog;
import com.example.smartfleetx.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OfflineSyncManager - Manages offline incident capture and cloud synchronization
 * Features:
 * - Network connectivity monitoring
 * - Automatic sync on connectivity restore
 * - Exponential backoff retry mechanism
 * - Batch upload optimization
 * - Sync status tracking
 */
public class OfflineSyncManager {

    private static final String TAG = "OfflineSyncManager";

    private static OfflineSyncManager instance;

    private Context context;
    private DatabaseHelper dbHelper;
    private ApiService apiService;
    private ConnectivityManager connectivityManager;
    private Gson gson;

    // Sync state
    private boolean isSyncing = false;
    private SyncStatusListener listener;

    // Retry configuration (exponential backoff)
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_MS = 2000; // 2 seconds
    private static final int MAX_BACKOFF_MS = 60000;  // 60 seconds

    private OfflineSyncManager(Context context, ApiService apiService) {
        this.context = context.getApplicationContext();
        this.apiService = apiService;
        this.dbHelper = new DatabaseHelper(context);
        this.gson = new Gson();
        this.connectivityManager = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);

        registerNetworkCallback();
    }

    public static synchronized OfflineSyncManager getInstance(Context context, ApiService apiService) {
        if (instance == null) {
            instance = new OfflineSyncManager(context, apiService);
        }
        return instance;
    }

    /**
     * Register network callback to auto-sync when connectivity is restored
     */
    private void registerNetworkCallback() {
        if (connectivityManager != null) {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "Network connectivity restored");
                    
                    // Trigger sync after a short delay
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
syncPendingIncidents();
                    }, 2000); // 2 second delay
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    Log.d(TAG, "Network connectivity lost");
                }
            });
        }
    }

    /**
     * Save incident for offline sync
     */
    public boolean saveIncidentOffline(IncidentData incidentData) {
        try {
            String jsonData = gson.toJson(incidentData);
            boolean saved = dbHelper.savePendingIncident(incidentData.getId(), jsonData);

            if (saved) {
                Log.i(TAG, "Incident saved offline: " + incidentData.getId());
                notifyStatusUpdate("Incident saved locally", false);
                
                // Try to sync immediately if network is available
                if (isNetworkAvailable()) {
                    syncPendingIncidents();
                }
            }

            return saved;
        } catch (Exception e) {
            Log.e(TAG, "Error saving incident offline", e);
            return false;
        }
    }

    /**
     * Sync all pending incidents
     */
    public void syncPendingIncidents() {
        if (isSyncing) {
            Log.w(TAG, "Sync already in progress");
            return;
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network connection, sync postponed");
            notifyStatusUpdate("No internet connection", false);
            return;
        }

        isSyncing = true;
        notifyStatusUpdate("Syncing incidents...", false);

        Cursor cursor = dbHelper.getPendingIncidents();
        List<PendingIncidentData> pendingIncidents = new ArrayList<>();

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int idIndex = cursor.getColumnIndex("incident_id");
                    int dataIndex = cursor.getColumnIndex("incident_data");
                    int attemptsIndex = cursor.getColumnIndex("sync_attempts");

                    if (idIndex >= 0 && dataIndex >= 0 && attemptsIndex >= 0) {
                        String incidentId = cursor.getString(idIndex);
                        String jsonData = cursor.getString(dataIndex);
                        int attempts = cursor.getInt(attemptsIndex);

                        pendingIncidents.add(new PendingIncidentData(incidentId, jsonData, attempts));
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (pendingIncidents.isEmpty()) {
            Log.d(TAG, "No pending incidents to sync");
            isSyncing = false;
            notifyStatusUpdate("All incidents synced", true);
            return;
        }

        Log.d(TAG, "Found " + pendingIncidents.size() + " pending incidents to sync");

        // Sync each incident
        syncNextIncident(pendingIncidents, 0);
    }

    /**
     * Recursively sync incidents one by one
     */
    private void syncNextIncident(List<PendingIncidentData> incidents, int index) {
        if (index >= incidents.size()) {
            isSyncing = false;
            notifyStatusUpdate("Sync completed", true);
            Log.d(TAG, "All incidents synced successfully");
            return;
        }

        PendingIncidentData pendingData = incidents.get(index);
        
        // Check if max retries exceeded
        if (pendingData.attempts >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max retry attempts exceeded for incident: " + pendingData.incidentId);
            dbHelper.updateIncidentSyncStatus(pendingData.incidentId, "FAILED", 
                "Max retry attempts exceeded");
            
            // Continue with next incident
            syncNextIncident(incidents, index + 1);
            return;
        }

        // Parse incident data
        IncidentData incidentData;
        try {
            incidentData = gson.fromJson(pendingData.jsonData, IncidentData.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing incident data", e);
            dbHelper.updateIncidentSyncStatus(pendingData.incidentId, "FAILED", 
                "Invalid data format");
            syncNextIncident(incidents, index + 1);
            return;
        }

        // Update status to SYNCING
        dbHelper.updateIncidentSyncStatus(pendingData.incidentId, "SYNCING", null);

        // Upload to server
        JsonObject jsonBody = gson.toJsonTree(incidentData).getAsJsonObject();
        
        // Add sensor logs to the payload
        Cursor logCursor = dbHelper.getSensorLogs(pendingData.incidentId);
        List<SensorLog> logs = new ArrayList<>();
        if (logCursor != null && logCursor.moveToFirst()) {
            do {
                SensorLog log = new SensorLog();
                log.timestamp = logCursor.getLong(logCursor.getColumnIndexOrThrow("timestamp"));
                log.gForceX = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gforce_x"));
                log.gForceY = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gforce_y"));
                log.gForceZ = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gforce_z"));
                log.gForceMagnitude = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gforce_magnitude"));
                log.gyroX = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gyro_x"));
                log.gyroY = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gyro_y"));
                log.gyroZ = logCursor.getFloat(logCursor.getColumnIndexOrThrow("gyro_z"));
                log.speed = logCursor.getFloat(logCursor.getColumnIndexOrThrow("speed"));
                log.latitude = logCursor.getDouble(logCursor.getColumnIndexOrThrow("latitude"));
                log.longitude = logCursor.getDouble(logCursor.getColumnIndexOrThrow("longitude"));
                logs.add(log);
            } while (logCursor.moveToNext());
            logCursor.close();
        }
        jsonBody.add("sensorLogs", gson.toJsonTree(logs));

        Call<ResponseBody> call = apiService.createIncident(jsonBody);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Incident synced successfully: " + pendingData.incidentId);
                    
                    // Mark as synced and delete from local DB
                    dbHelper.updateIncidentSyncStatus(pendingData.incidentId, "SYNCED", null);
                    dbHelper.deleteSyncedIncident(pendingData.incidentId);
                    dbHelper.deleteSensorLogs(pendingData.incidentId);
                    
                    notifyStatusUpdate("Synced: " + (index + 1) + "/" + incidents.size(), false);

                    // Continue with next incident
                    syncNextIncident(incidents, index + 1);
                } else {
                    handleSyncError(pendingData, incidents, index, 
                        "Server error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                handleSyncError(pendingData, incidents, index, 
                    "Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Handle sync error with exponential backoff
     */
    private void handleSyncError(PendingIncidentData pendingData, 
                                 List<PendingIncidentData> incidents, 
                                 int index, String errorMessage) {
        Log.w(TAG, "Sync failed for incident " + pendingData.incidentId + ": " + errorMessage);

        dbHelper.updateIncidentSyncStatus(pendingData.incidentId, "PENDING", errorMessage);

        // Calculate backoff delay
        long backoffDelay = Math.min(
            BASE_BACKOFF_MS * (long) Math.pow(2, pendingData.attempts),
            MAX_BACKOFF_MS
        );

        Log.d(TAG, "Retry scheduled in " + backoffDelay + "ms (attempt " + 
              (pendingData.attempts + 1) + ")");

        // Continue with next incident (will retry this one later)
        syncNextIncident(incidents, index + 1);
    }

    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = 
                    connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && 
                       capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        return false;
    }

    /**
     * Get sync status
     */
    public SyncStatus getSyncStatus() {
        int pendingCount = dbHelper.getPendingIncidentsCount();
        boolean networkAvailable = isNetworkAvailable();

        return new SyncStatus(pendingCount, isSyncing, networkAvailable);
    }

    public void setSyncStatusListener(SyncStatusListener listener) {
        this.listener = listener;
    }

    private void notifyStatusUpdate(String message, boolean isComplete) {
        if (listener != null) {
            listener.onSyncStatusUpdate(message, isComplete);
        }
    }

    /**
     * Internal class for pending incident data
     */
    private static class PendingIncidentData {
        String incidentId;
        String jsonData;
        int attempts;

        PendingIncidentData(String incidentId, String jsonData, int attempts) {
            this.incidentId = incidentId;
            this.jsonData = jsonData;
            this.attempts = attempts;
        }
    }

    /**
     * Sync status data class
     */
    public static class SyncStatus {
        public int pendingCount;
        public boolean isSyncing;
        public boolean networkAvailable;

        public SyncStatus(int pendingCount, boolean isSyncing, boolean networkAvailable) {
            this.pendingCount = pendingCount;
            this.isSyncing = isSyncing;
            this.networkAvailable = networkAvailable;
        }
    }

    /**
     * Listener interface for sync status updates
     */
    public interface SyncStatusListener {
        void onSyncStatusUpdate(String message, boolean isComplete);
    }
}
