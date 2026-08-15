package com.example.smartfleetx.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


import com.example.smartfleetx.model.SensorLog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.example.smartfleetx.model.IncidentData;
import com.example.smartfleetx.model.SensorLog;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "smartfleetx.db";
    private static final int DATABASE_VERSION = 4; // Updated for system health logs

    // Users table
    private static final String TABLE_USERS = "users";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_EMAIL = "email";
    private static final String COL_PHONE = "phone";
    private static final String COL_PASSWORD = "password";
    private static final String COL_CREATED_AT = "created_at";

    // Pending incidents table (for offline sync)
    private static final String TABLE_PENDING_INCIDENTS = "pending_incidents";
    
    // Sensor logs table
    private static final String TABLE_SENSOR_LOGS = "sensor_logs";

    // Alert logs table
    private static final String TABLE_ALERT_LOGS = "alert_logs";

    // System health logs table
    private static final String TABLE_SYSTEM_HEALTH_LOGS = "system_health_logs";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_EMAIL + " TEXT UNIQUE NOT NULL, " +
                COL_PHONE + " TEXT, " +
                COL_PASSWORD + " TEXT NOT NULL, " +
                COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        db.execSQL(createUsersTable);

        // Create pending incidents table for offline sync
        String createPendingIncidentsTable = "CREATE TABLE " + TABLE_PENDING_INCIDENTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "incident_id TEXT UNIQUE NOT NULL, " +
                "incident_data TEXT NOT NULL, " +  // JSON serialized IncidentData
                "created_at INTEGER NOT NULL, " +
                "sync_attempts INTEGER DEFAULT 0, " +
                "last_sync_attempt INTEGER, " +
                "sync_status TEXT DEFAULT 'PENDING', " + // PENDING, SYNCING, FAILED, SYNCED
                "error_message TEXT" +
                ")";
        db.execSQL(createPendingIncidentsTable);

        // Create sensor logs table
        String createSensorLogsTable = "CREATE TABLE " + TABLE_SENSOR_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "incident_id TEXT, " +
                "timestamp INTEGER NOT NULL, " +
                "gforce_x REAL, " +
                "gforce_y REAL, " +
                "gforce_z REAL, " +
                "gforce_magnitude REAL, " +
                "gyro_x REAL, " +
                "gyro_y REAL, " +
                "gyro_z REAL, " +
                "speed REAL, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "FOREIGN KEY(incident_id) REFERENCES " + TABLE_PENDING_INCIDENTS + "(incident_id)" +
                ")";
        db.execSQL(createSensorLogsTable);

        // Create alert logs table
        String createAlertLogsTable = "CREATE TABLE " + TABLE_ALERT_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "incident_id TEXT, " +
                "contact_name TEXT, " +
                "phone_number TEXT, " +
                "status TEXT, " +
                "details TEXT, " +
                "timestamp INTEGER NOT NULL, " +
                "FOREIGN KEY(incident_id) REFERENCES " + TABLE_PENDING_INCIDENTS + "(incident_id)" +
                ")";
        db.execSQL(createAlertLogsTable);

        // Create system health logs table
        String createHealthLogsTable = "CREATE TABLE " + TABLE_SYSTEM_HEALTH_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp INTEGER NOT NULL, " +
                "status TEXT, " +
                "overall_score REAL, " +
                "uptime_ms INTEGER, " +
                "battery_level INTEGER, " +
                "storage_free_mb INTEGER, " +
                "network_status TEXT" +
                ")";
        db.execSQL(createHealthLogsTable);

        // Create indexes for better query performance
        db.execSQL("CREATE INDEX idx_incident_id ON " + TABLE_SENSOR_LOGS + "(incident_id)");
        db.execSQL("CREATE INDEX idx_sync_status ON " + TABLE_PENDING_INCIDENTS + "(sync_status)");

        // Create default admin user
        insertDefaultUser(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add new tables for version 2
            String createPendingIncidentsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_PENDING_INCIDENTS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "incident_id TEXT UNIQUE NOT NULL, " +
                    "incident_data TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "sync_attempts INTEGER DEFAULT 0, " +
                    "last_sync_attempt INTEGER, " +
                    "sync_status TEXT DEFAULT 'PENDING', " +
                    "error_message TEXT" +
                    ")";
            db.execSQL(createPendingIncidentsTable);

            String createSensorLogsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_SENSOR_LOGS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "incident_id TEXT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "gforce_x REAL, " +
                    "gforce_y REAL, " +
                    "gforce_z REAL, " +
                    "gforce_magnitude REAL, " +
                    "gyro_x REAL, " +
                    "gyro_y REAL, " +
                    "gyro_z REAL, " +
                    "speed REAL, " +
                    "latitude REAL, " +
                    "longitude REAL" +
                    ")";
            db.execSQL(createSensorLogsTable);

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_incident_id ON " + TABLE_SENSOR_LOGS + "(incident_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_status ON " + TABLE_PENDING_INCIDENTS + "(sync_status)");
        }
        
        if (oldVersion < 3) {
            String createAlertLogsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_ALERT_LOGS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "incident_id TEXT, " +
                    "contact_name TEXT, " +
                    "phone_number TEXT, " +
                    "status TEXT, " +
                    "details TEXT, " +
                    "timestamp INTEGER NOT NULL" +
                    ")";
            db.execSQL(createAlertLogsTable);
        }

        if (oldVersion < 4) {
            String createHealthLogsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_SYSTEM_HEALTH_LOGS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "status TEXT, " +
                    "overall_score REAL, " +
                    "uptime_ms INTEGER, " +
                    "battery_level INTEGER, " +
                    "storage_free_mb INTEGER, " +
                    "network_status TEXT" +
                    ")";
            db.execSQL(createHealthLogsTable);
        }
    }

    private void insertDefaultUser(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, "Admin User");
        values.put(COL_EMAIL, "admin@fleet.com");
        values.put(COL_PHONE, "");
        values.put(COL_PASSWORD, hashPassword("admin123"));
        
        db.insert(TABLE_USERS, null, values);
        Log.d(TAG, "Default admin user created: admin@fleet.com / admin123");
    }

    // Register new user
    public boolean registerUser(String name, String email, String phone, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Check if email already exists
        if (emailExists(email)) {
            Log.d(TAG, "Email already exists: " + email);
            return false;
        }

        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_EMAIL, email.toLowerCase().trim());
        values.put(COL_PHONE, phone);
        values.put(COL_PASSWORD, hashPassword(password));

        long result = db.insert(TABLE_USERS, null, values);
        db.close();

        if (result == -1) {
            Log.d(TAG, "Failed to register user: " + email);
            return false;
        } else {
            Log.d(TAG, "User registered successfully: " + email);
            return true;
        }
    }

    // Authenticate user
    public boolean authenticateUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String hashedPassword = hashPassword(password);

        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " +
                COL_EMAIL + " = ? AND " + COL_PASSWORD + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{email.toLowerCase().trim(), hashedPassword});
        boolean authenticated = cursor.getCount() > 0;
        
        cursor.close();
        db.close();

        Log.d(TAG, "Authentication for " + email + ": " + authenticated);
        return authenticated;
    }

    // Check if email exists
    public boolean emailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + COL_EMAIL + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{email.toLowerCase().trim()});
        boolean exists = cursor.getCount() > 0;
        
        cursor.close();
        db.close();
        
        return exists;
    }

    // Get user details
    public Cursor getUserByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + COL_EMAIL + " = ?";
        return db.rawQuery(query, new String[]{email.toLowerCase().trim()});
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing password", e);
            return password; // Fallback (not secure, but won't crash)
        }
    }

    // Delete user (for admin purposes)
    public boolean deleteUser(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_USERS, COL_EMAIL + " = ?", 
                new String[]{email.toLowerCase().trim()});
        db.close();
        return result > 0;
    }

    // Get total user count
    public int getUserCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // ========== Pending Incidents Management ==========

    /**
     * Save incident for offline sync
     */
    public boolean savePendingIncident(String incidentId, String incidentJsonData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("incident_id", incidentId);
        values.put("incident_data", incidentJsonData);
        values.put("created_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");

        long result = db.insert(TABLE_PENDING_INCIDENTS, null, values);
        db.close();

        Log.d(TAG, "Pending incident saved: " + incidentId + " (result: " + result + ")");
        return result != -1;
    }

    /**
     * Get all pending incidents
     */
    public Cursor getPendingIncidents() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PENDING_INCIDENTS,
                null,
                "sync_status = ?",
                new String[]{"PENDING"},
                null, null,
                "created_at ASC");
    }

    /**
     * Update incident sync status
     */
    public boolean updateIncidentSyncStatus(String incidentId, String status, String errorMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("sync_status", status);
        values.put("last_sync_attempt", System.currentTimeMillis());
        
        if (errorMessage != null) {
            values.put("error_message", errorMessage);
        }

        // Increment sync attempts
        db.execSQL("UPDATE " + TABLE_PENDING_INCIDENTS + 
                  " SET sync_attempts = sync_attempts + 1 WHERE incident_id = ?",
                  new String[]{incidentId});

        int rowsAffected = db.update(TABLE_PENDING_INCIDENTS, values,
                "incident_id = ?",
                new String[]{incidentId});
        db.close();

        return rowsAffected > 0;
    }

    /**
     * Delete synced incident
     */
    public boolean deleteSyncedIncident(String incidentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_PENDING_INCIDENTS,
                "incident_id = ?",
                new String[]{incidentId});
        db.close();
        return result > 0;
    }

    /**
     * Get pending incidents count
     */
    public int getPendingIncidentsCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_PENDING_INCIDENTS + 
                " WHERE sync_status = 'PENDING'", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    // ========== Sensor Logs Management ==========

    /**
     * Save sensor logs for an incident
     */
    public boolean saveSensorLogs(String incidentId, List<SensorLog> logs) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        
        try {
            for (com.example.smartfleetx.model.SensorLog log : logs) {
                ContentValues values = new ContentValues();
                values.put("incident_id", incidentId);
                values.put("timestamp", log.timestamp);
                values.put("gforce_x", log.gForceX);
                values.put("gforce_y", log.gForceY);
                values.put("gforce_z", log.gForceZ);
                values.put("gforce_magnitude", log.gForceMagnitude);
                values.put("gyro_x", log.gyroX);
                values.put("gyro_y", log.gyroY);
                values.put("gyro_z", log.gyroZ);
                values.put("speed", log.speed);
                values.put("latitude", log.latitude);
                values.put("longitude", log.longitude);

                db.insert(TABLE_SENSOR_LOGS, null, values);
            }
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Saved " + logs.size() + " sensor logs for incident: " + incidentId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving sensor logs", e);
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * Get sensor logs for an incident
     */
    public Cursor getSensorLogs(String incidentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_SENSOR_LOGS,
                null,
                "incident_id = ?",
                new String[]{incidentId},
                null, null,
                "timestamp ASC");
    }

    /**
     * Delete sensor logs for an incident
     */
    public boolean deleteSensorLogs(String incidentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_SENSOR_LOGS,
                "incident_id = ?",
                new String[]{incidentId});
        db.close();
        return result > 0;
    }

    // ========== Alert Logs Management ==========

    /**
     * Save an alert log entry
     */
    public boolean saveAlertLog(String incidentId, String name, String phone, String status, String details) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("incident_id", incidentId);
        values.put("contact_name", name);
        values.put("phone_number", phone);
        values.put("status", status);
        values.put("details", details);
        values.put("timestamp", System.currentTimeMillis());

        long result = db.insert(TABLE_ALERT_LOGS, null, values);
        db.close();
        return result != -1;
    }

    /**
     * Get alert logs for an incident
     */
    public Cursor getAlertLogs(String incidentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_ALERT_LOGS,
                null,
                "incident_id = ?",
                new String[]{incidentId},
                null, null,
                "timestamp ASC");
    }

    /**
     * Get incident data by ID
     */
    public IncidentData getIncidentData(String incidentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PENDING_INCIDENTS, null, "incident_id = ?", 
            new String[]{incidentId}, null, null, null);
            
        if (cursor != null && cursor.moveToFirst()) {
            String jsonData = cursor.getString(cursor.getColumnIndexOrThrow("incident_data"));
            cursor.close();
            return new com.google.gson.Gson().fromJson(jsonData, IncidentData.class);
        }
        if (cursor != null) cursor.close();
        return null;
    }

    // ========== System Health Logs Management ==========

    /**
     * Save system health log
     */
    public boolean saveHealthLog(long timestamp, String status, float score, long uptime, 
                                 int battery, long storageFree, String network) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("timestamp", timestamp);
        values.put("status", status);
        values.put("overall_score", score);
        values.put("uptime_ms", uptime);
        values.put("battery_level", battery);
        values.put("storage_free_mb", storageFree);
        values.put("network_status", network);

        long result = db.insert(TABLE_SYSTEM_HEALTH_LOGS, null, values);
        db.close();
        return result != -1;
    }

    /**
     * Get system health logs (e.g., last 24 hours)
     */
    public Cursor getHealthLogs(long sinceTimestamp) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_SYSTEM_HEALTH_LOGS,
                null,
                "timestamp >= ?",
                new String[]{String.valueOf(sinceTimestamp)},
                null, null,
                "timestamp DESC");
    }
}
