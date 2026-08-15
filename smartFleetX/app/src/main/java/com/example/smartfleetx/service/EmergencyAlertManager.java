package com.example.smartfleetx.service;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * EmergencyAlertManager - Manages emergency notifications on accident detection
 * Features:
 * - Instant SMS alert generation
 * - GPS location sharing
 * - Multi-contact notification (up to 5)
 * - Alert retry mechanism (3 attempts, 30s interval)
 * - Response timeline logging
 */
public class EmergencyAlertManager extends Service {

    private static final String TAG = "EmergencyAlertManager";

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_INTERVAL_MS = 30000; // 30 seconds

    // SharedPreferences keys
    private static final String PREFS_NAME = "EmergencyContacts";
    private static final String KEY_CONTACTS = "contacts";
    private static final String KEY_ENABLED = "alerts_enabled";

    private SharedPreferences prefs;
    private Gson gson;
    private Handler retryHandler;
    private com.example.smartfleetx.database.DatabaseHelper databaseHelper;

    // Alert state
    private List<EmergencyContact> emergencyContacts;
    private AlertStatusListener statusListener;

    private final IBinder binder = new EmergencyAlertBinder();

    public class EmergencyAlertBinder extends Binder {
        public EmergencyAlertManager getService() {
            return EmergencyAlertManager.this;
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
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        retryHandler = new Handler(Looper.getMainLooper());
        databaseHelper = new com.example.smartfleetx.database.DatabaseHelper(this);
        loadEmergencyContacts();
        Log.d(TAG, "EmergencyAlertManager service created");
    }

    /**
     * Send emergency alert to all contacts
     */
    public void sendEmergencyAlert(String severity, Location location, String incidentId) {
        if (!isAlertsEnabled()) {
            Log.w(TAG, "Emergency alerts are disabled");
            return;
        }

        if (emergencyContacts == null || emergencyContacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured");
            notifyStatus("No emergency contacts configured", false);
            return;
        }

        if (!hasSmsPermission()) {
            Log.e(TAG, "SMS permission not granted");
            notifyStatus("SMS permission required", false);
            return;
        }

        Log.i(TAG, String.format("Sending emergency alerts for %s incident to %d contacts",
                severity, emergencyContacts.size()));

        // Create alert message
        String message = createAlertMessage(severity, location, incidentId);

        // Send to all contacts
        for (EmergencyContact contact : emergencyContacts) {
            if (contact.isEnabled()) {
                sendAlertToContact(contact, message, 0, incidentId);
            }
        }

        notifyStatus("Emergency alerts sent", true);
    }

    /**
     * Create formatted alert message
     */
    private String createAlertMessage(String severity, Location location, String incidentId) {
        StringBuilder message = new StringBuilder();
        
        message.append("🚨 EMERGENCY ALERT 🚨\n\n");
        message.append(severity).append(" ACCIDENT DETECTED\n\n");

        // Timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        message.append("Time: ").append(sdf.format(new Date())).append("\n");

        // Location
        if (location != null) {
            message.append(String.format("Location: %.6f, %.6f\n", 
                location.getLatitude(), location.getLongitude()));
            
            // Google Maps link
            String mapsUrl = String.format("https://maps.google.com/?q=%.6f,%.6f",
                location.getLatitude(), location.getLongitude());
            message.append("Map: ").append(mapsUrl).append("\n");
        }

        message.append("\nIncident ID: ").append(incidentId).append("\n");
        message.append("\nPlease respond immediately!");

        return message.toString();
    }

    /**
     * Send alert to specific contact with retry mechanism
     */
    private void sendAlertToContact(EmergencyContact contact, String message, int attempt, String incidentId) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max retry attempts reached for contact: " + contact.getName());
            logAlertResponse(incidentId, contact, "FAILED", "Max retries exceeded");
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            
            // Split message if too long
            ArrayList<String> parts = smsManager.divideMessage(message);
            
            if (parts.size() == 1) {
                smsManager.sendTextMessage(contact.getPhoneNumber(), null, message, null, null);
            } else {
                smsManager.sendMultipartTextMessage(contact.getPhoneNumber(), null, parts, 
                    null, null);
            }

            Log.i(TAG, String.format("Alert sent to %s (%s) - Priority: %s",
                contact.getName(), contact.getPhoneNumber(), contact.getPriority()));
            
            logAlertResponse(incidentId, contact, "SENT", "Attempt " + (attempt + 1));

        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS to " + contact.getName(), e);
            
            // Retry after delay
            final int nextAttempt = attempt + 1;
            retryHandler.postDelayed(() -> {
                Log.d(TAG, "Retrying alert to " + contact.getName() + 
                      " (attempt " + (nextAttempt + 1) + ")");
                sendAlertToContact(contact, message, nextAttempt, incidentId);
            }, RETRY_INTERVAL_MS);
            
            logAlertResponse(incidentId, contact, "RETRY_SCHEDULED", e.getMessage());
        }
    }

    /**
     * Log alert response timeline
     */
    private void logAlertResponse(String incidentId, EmergencyContact contact, String status, String details) {
        long timestamp = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        
        Log.d(TAG, String.format("[%s] %s - %s: %s (%s)",
            sdf.format(new Date(timestamp)),
            contact.getName(),
            contact.getPriority(),
            status,
            details
        ));

        // Save to database for audit trail
        if (databaseHelper != null) {
            databaseHelper.saveAlertLog(incidentId, contact.getName(), 
                contact.getPhoneNumber(), status, details);
        }
    }

    // ========== Emergency Contacts Management ==========

    /**
     * Load emergency contacts from SharedPreferences
     */
    private void loadEmergencyContacts() {
        String json = prefs.getString(KEY_CONTACTS, null);
        if (json != null) {
            Type listType = new TypeToken<List<EmergencyContact>>(){}.getType();
            emergencyContacts = gson.fromJson(json, listType);
        } else {
            emergencyContacts = new ArrayList<>();
        }
        Log.d(TAG, "Loaded " + emergencyContacts.size() + " emergency contacts");
    }

    /**
     * Save emergency contacts to SharedPreferences
     */
    private void saveEmergencyContacts() {
        String json = gson.toJson(emergencyContacts);
        prefs.edit().putString(KEY_CONTACTS, json).apply();
        Log.d(TAG, "Saved " + emergencyContacts.size() + " emergency contacts");
    }

    /**
     * Add emergency contact
     */
    public boolean addEmergencyContact(EmergencyContact contact) {
        if (emergencyContacts.size() >= 5) {
            Log.w(TAG, "Maximum 5 emergency contacts allowed");
            return false;
        }

        // Check for duplicates
        for (EmergencyContact existing : emergencyContacts) {
            if (existing.getPhoneNumber().equals(contact.getPhoneNumber())) {
                Log.w(TAG, "Contact already exists: " + contact.getPhoneNumber());
                return false;
            }
        }

        emergencyContacts.add(contact);
        saveEmergencyContacts();
        Log.i(TAG, "Added emergency contact: " + contact.getName());
        return true;
    }

    /**
     * Remove emergency contact
     */
    public boolean removeEmergencyContact(String phoneNumber) {
        boolean removed = emergencyContacts.removeIf(c -> c.getPhoneNumber().equals(phoneNumber));
        if (removed) {
            saveEmergencyContacts();
            Log.i(TAG, "Removed emergency contact: " + phoneNumber);
        }
        return removed;
    }

    /**
     * Update emergency contact
     */
    public boolean updateEmergencyContact(EmergencyContact updatedContact) {
        for (int i = 0; i < emergencyContacts.size(); i++) {
            if (emergencyContacts.get(i).getPhoneNumber().equals(updatedContact.getPhoneNumber())) {
                emergencyContacts.set(i, updatedContact);
                saveEmergencyContacts();
                Log.i(TAG, "Updated emergency contact: " + updatedContact.getName());
                return true;
            }
        }
        return false;
    }

    /**
     * Get all emergency contacts
     */
    public List<EmergencyContact> getEmergencyContacts() {
        return new ArrayList<>(emergencyContacts);
    }

    /**
     * Enable/disable emergency alerts
     */
    public void setAlertsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.d(TAG, "Emergency alerts " + (enabled ? "enabled" : "disabled"));
    }

    public boolean isAlertsEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true); // Default enabled
    }

    /**
     * Check SMS permission
     */
    private boolean hasSmsPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            == PackageManager.PERMISSION_GRANTED;
    }

    public void setAlertStatusListener(AlertStatusListener listener) {
        this.statusListener = listener;
    }

    private void notifyStatus(String message, boolean success) {
        if (statusListener != null) {
            statusListener.onAlertStatusUpdate(message, success);
        }
    }

    /**
     * Emergency contact model
     */
    public static class EmergencyContact {
        private String name;
        private String phoneNumber;
        private String priority;  // "PRIMARY", "SECONDARY", "TERTIARY"
        private boolean enabled;

        public EmergencyContact(String name, String phoneNumber, String priority) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.priority = priority;
            this.enabled = true;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * Listener interface for alert status updates
     */
    public interface AlertStatusListener {
        void onAlertStatusUpdate(String message, boolean success);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        retryHandler.removeCallbacksAndMessages(null);
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Log.d(TAG, "EmergencyAlertManager service destroyed");
    }
}
