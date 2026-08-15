package com.example.smartfleetx.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.smartfleetx.network.RetrofitClient;
import com.example.smartfleetx.service.OfflineSyncManager;

/**
 * SyncWorker - Background sync worker using WorkManager
 * Periodically checks and syncs pending incidents
 * Runs with network constraints
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SyncWorker started");

        try {
            // Get OfflineSyncManager instance
            OfflineSyncManager syncManager = OfflineSyncManager.getInstance(
                getApplicationContext(),
                RetrofitClient.getApiService()
            );

            // Check sync status
            OfflineSyncManager.SyncStatus status = syncManager.getSyncStatus();

            if (status.pendingCount > 0 && status.networkAvailable) {
                Log.d(TAG, "Found " + status.pendingCount + " pending incidents, starting sync...");
                
                // Trigger sync
                syncManager.syncPendingIncidents();
                
                // Return success
                return Result.success();
            } else {
                Log.d(TAG, "No pending incidents or no network, nothing to sync");
                return Result.success();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during sync", e);
            
            // Retry on failure
            if (getRunAttemptCount() < 3) {
                return Result.retry();
            } else {
                return Result.failure();
            }
        }
    }
}
