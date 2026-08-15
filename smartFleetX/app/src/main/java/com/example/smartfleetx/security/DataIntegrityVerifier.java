package com.example.smartfleetx.security;

import android.util.Log;

import com.example.smartfleetx.model.IncidentData;
import com.example.smartfleetx.model.SensorLog;
import com.google.gson.Gson;

import java.security.MessageDigest;
import java.util.List;

/**
 * DataIntegrityVerifier - Ensures accident data integrity for legal evidence
 * Features:
 * - SHA-256 cryptographic hashing
 * - Tampering detection
 * - Chain-of-custody logging
 * - Integrity scoring (0-1)
 */
public class DataIntegrityVerifier {

    private static final String TAG = "DataIntegrityVerifier";

    private Gson gson;

    public DataIntegrityVerifier() {
        this.gson = new Gson();
    }

    /**
     * Generate SHA-256 hash for incident data
     *
     * @param incidentData Incident data to hash
     * @param sensorLogs Associated sensor logs
     * @return SHA-256 hash string
     */
    public String generateDataHash(IncidentData incidentData, List<SensorLog> sensorLogs) {
        try {
            // Create canonical representation of data
            StringBuilder dataString = new StringBuilder();
            
            // Incident core data
            dataString.append(incidentData.getId());
            dataString.append("|");
            dataString.append(incidentData.getTimestamp());
            dataString.append("|");
            dataString.append(incidentData.getSeverity());
            dataString.append("|");
            dataString.append(incidentData.getImpactForce());
            dataString.append("|");
            dataString.append(incidentData.getLatitude());
            dataString.append("|");
            dataString.append(incidentData.getLongitude());
            
            // Sensor logs (if available)
            if (sensorLogs != null) {
                for (SensorLog log : sensorLogs) {
                    dataString.append("|");
                    dataString.append(log.timestamp);
                    dataString.append(",");
                    dataString.append(log.gForceMagnitude);
                }
            }

            // Compute SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(dataString.toString().getBytes("UTF-8"));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            String hash = hexString.toString();
            Log.d(TAG, "Generated data hash: " + hash.substring(0, 16) + "...");
            
            return hash;

        } catch (Exception e) {
            Log.e(TAG, "Error generating data hash", e);
            return null;
        }
    }

    /**
     * Verify data integrity by comparing hashes
     *
     * @param incidentData Current incident data
     * @param sensorLogs Current sensor logs
     * @param originalHash Original hash to compare against
     * @return true if data is intact, false if tampered
     */
    public boolean verifyIntegrity(IncidentData incidentData, List<SensorLog> sensorLogs, 
                                   String originalHash) {
        if (originalHash == null || originalHash.isEmpty()) {
            Log.w(TAG, "No original hash available for verification");
            return false;
        }

        String currentHash = generateDataHash(incidentData, sensorLogs);
        
        if (currentHash == null) {
            return false;
        }

        boolean isIntact = currentHash.equals(originalHash);
        
        if (isIntact) {
            Log.i(TAG, "Data integrity verified - NO tampering detected");
        } else {
            Log.w(TAG, "Data integrity check FAILED - TAMPERING detected!");
            Log.w(TAG, "Original: " + originalHash.substring(0, 16) + "...");
            Log.w(TAG, "Current:  " + currentHash.substring(0, 16) + "...");
        }

        return isIntact;
    }

    /**
     * Calculate comprehensive integrity score (0-1)
     * 
     * Factors:
     * - Hash match (40%)
     * - Timestamp consistency (20%)
     * - Data completeness (20%)
     * - Sensor data consistency (20%)
     *
     * @return Integrity score 0.0 (tampered) to 1.0 (perfect integrity)
     */
    public float calculateIntegrityScore(IncidentData incidentData, List<SensorLog> sensorLogs) {
        float score = 0.0f;

        // Factor 1: Hash verification (40%)
        if (incidentData.getDataHash() != null) {
            boolean hashMatch = verifyIntegrity(incidentData, sensorLogs, incidentData.getDataHash());
            if (hashMatch) {
                score += 0.40f;
            }
        }

        // Factor 2: Timestamp consistency (20%)
        if (isTimestampConsistent(incidentData, sensorLogs)) {
            score += 0.20f;
        }

        // Factor 3: Data completeness (20%)
        float completeness = calculateDataCompleteness(incidentData);
        score += 0.20f * completeness;

        // Factor 4: Sensor data consistency (20%)
        if (sensorLogs != null && !sensorLogs.isEmpty()) {
            float sensorConsistency = calculateSensorConsistency(sensorLogs);
            score += 0.20f * sensorConsistency;
        } else {
            // No sensor logs, but this doesn't mean tampering
            score += 0.10f; // Partial credit
        }

        Log.d(TAG, String.format("Calculated integrity score: %.2f", score));
        return Math.max(0.0f, Math.min(1.0f, score));
    }

    /**
     * Check if timestamps are consistent
     */
    private boolean isTimestampConsistent(IncidentData incidentData, List<SensorLog> sensorLogs) {
        long incidentTime = incidentData.getTimestamp();
        
        if (sensorLogs == null || sensorLogs.isEmpty()) {
            return true; // Can't check, assume consistent
        }

        // Check if sensor logs are within reasonable time window of incident (±30 seconds)
        long tolerance = 30000; // 30 seconds
        
        for (SensorLog log : sensorLogs) {
            long timeDiff = Math.abs(log.timestamp - incidentTime);
            if (timeDiff > tolerance) {
                Log.w(TAG, "Timestamp inconsistency detected: " + timeDiff + "ms difference");
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate data completeness (0-1)
     * Checks if all essential fields are populated
     */
    private float calculateDataCompleteness(IncidentData incidentData) {
        int totalFields = 10;
        int populatedFields = 0;

        if (incidentData.getId() != null) populatedFields++;
        if (incidentData.getTimestamp() > 0) populatedFields++;
        if (incidentData.getSeverity() != null) populatedFields++;
        if (incidentData.getImpactForce() > 0) populatedFields++;
        if (incidentData.getLatitude() != 0) populatedFields++;
        if (incidentData.getLongitude() != 0) populatedFields++;
        if (incidentData.getVideoPath() != null) populatedFields++;
        if (incidentData.getConfidenceScore() > 0) populatedFields++;
        if (incidentData.getVehicleData() != null) populatedFields++;
        if (incidentData.getSensorLogIds() != null && !incidentData.getSensorLogIds().isEmpty()) {
            populatedFields++;
        }

        float completeness = (float) populatedFields / totalFields;
        Log.d(TAG, String.format("Data completeness: %d/%d (%.1f%%)", 
            populatedFields, totalFields, completeness * 100));
        
        return completeness;
    }

    /**
     * Calculate sensor data consistency (0-1)
     * Checks for outliers and anomalies in sensor data
     */
    private float calculateSensorConsistency(List<SensorLog> sensorLogs) {
        if (sensorLogs.size() < 3) {
            return 1.0f; // Not enough data to check
        }

        int anomalies = 0;
        int totalChecks = 0;

        // Check for sudden impossible jumps in values
        for (int i = 1; i < sensorLogs.size(); i++) {
            SensorLog prev = sensorLogs.get(i - 1);
            SensorLog current = sensorLogs.get(i);

            // Check G-force jump (should not exceed 20G between samples at 10Hz)
            float gDiff = Math.abs(current.gForceMagnitude - prev.gForceMagnitude);
            if (gDiff > 20.0f) {
                anomalies++;
                Log.w(TAG, "Anomaly: G-force jump of " + gDiff + "G");
            }
            totalChecks++;

            // Check speed jump (should not exceed 50 m/s between samples)
            float speedDiff = Math.abs(current.speed - prev.speed);
            if (speedDiff > 50.0f) {
                anomalies++;
                Log.w(TAG, "Anomaly: Speed jump of " + speedDiff + " m/s");
            }
            totalChecks++;

            // Check timestamp sequence (should be monotonically increasing)
            if (current.timestamp <= prev.timestamp) {
                anomalies++;
                Log.w(TAG, "Anomaly: Timestamp not increasing");
            }
            totalChecks++;
        }

        float consistencyScore = 1.0f - ((float) anomalies / totalChecks);
        Log.d(TAG, String.format("Sensor consistency: %.1f%% (%d anomalies in %d checks)",
            consistencyScore * 100, anomalies, totalChecks));

        return Math.max(0.0f, consistencyScore);
    }

    /**
     * Update incident with integrity verification
     *
     * @param incidentData Incident to update
     * @param sensorLogs Associated sensor logs
     */
    public void updateIntegrityStatus(IncidentData incidentData, List<SensorLog> sensorLogs) {
        // Generate hash if not present
        if (incidentData.getDataHash() == null) {
            String hash = generateDataHash(incidentData, sensorLogs);
            incidentData.setDataHash(hash);
        }

        // Calculate integrity score
        float integrityScore = calculateIntegrityScore(incidentData, sensorLogs);
        incidentData.setIntegrityScore(integrityScore);

        // Set integrity status
        if (integrityScore >= 0.95f) {
            incidentData.setIntegrityStatus("VERIFIED");
        } else if (integrityScore >= 0.70f) {
            incidentData.setIntegrityStatus("UNVERIFIED");
        } else {
            incidentData.setIntegrityStatus("TAMPERED");
        }

        // Set verification timestamp
        incidentData.setVerifiedAt(System.currentTimeMillis());

        Log.i(TAG, String.format("Integrity status updated: %s (score: %.2f)",
            incidentData.getIntegrityStatus(), integrityScore));
    }

    /**
     * Log chain of custody event
     * For legal evidence tracking
     */
    public void logChainOfCustody(String incidentId, String event, String actor) {
        long timestamp = System.currentTimeMillis();
        
        String logEntry = String.format("[%d] Incident: %s, Event: %s, Actor: %s",
            timestamp, incidentId, event, actor);
        
        Log.i(TAG, "Chain of custody: " + logEntry);
        
        // In production, this would be stored in a dedicated audit log table
    }

    /**
     * Generate integrity report for legal purposes
     */
    public String generateIntegrityReport(IncidentData incidentData, List<SensorLog> sensorLogs) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== DATA INTEGRITY REPORT ===\n\n");
        report.append("Incident ID: ").append(incidentData.getId()).append("\n");
        report.append("Verification Time: ").append(new java.util.Date()).append("\n\n");
        
        // Hash verification
        report.append("Data Hash: ").append(incidentData.getDataHash() != null ? 
            incidentData.getDataHash().substring(0, 32) + "..." : "Not available").append("\n");
        
        // Integrity score
        float score = calculateIntegrityScore(incidentData, sensorLogs);
        report.append("Integrity Score: ").append(String.format("%.2f%%", score * 100)).append("\n");
        report.append("Status: ").append(incidentData.getIntegrityStatus()).append("\n\n");
        
        // Data completeness
        float completeness = calculateDataCompleteness(incidentData);
        report.append("Data Completeness: ").append(String.format("%.1f%%", completeness * 100)).append("\n");
        
        // Sensor consistency
        if (sensorLogs != null && !sensorLogs.isEmpty()) {
            float consistency = calculateSensorConsistency(sensorLogs);
            report.append("Sensor Consistency: ").append(String.format("%.1f%%", consistency * 100)).append("\n");
            report.append("Total Sensor Samples: ").append(sensorLogs.size()).append("\n");
        }
        
        report.append("\n=== END REPORT ===\n");
        
        return report.toString();
    }
}
