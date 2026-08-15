package com.example.smartfleetx.model;

import java.io.Serializable;

/**
 * Model class representing driver state at the time of an incident
 * Captures driver monitoring data from facial analysis
 */
public class DriverState implements Serializable {
    private boolean drowsy;           // Driver showing drowsiness
    private boolean distracted;       // Driver distracted (looking away)
    private float attentionScore;     // 0-100, higher is better
    private String headPose;          // "FORWARD", "LEFT", "RIGHT", "DOWN", "UP"
    private boolean eyesClosed;       // Eyes closed detection
    private boolean yawning;          // Yawning detected
    private boolean phoneUsage;       // Phone in hand detected
    private int blinksPerMinute;      // Blink rate
    private long timestamp;           // When this state was captured
    
    public DriverState() {
        this.headPose = "FORWARD";
        this.attentionScore = 100f;
    }
    
    // Getters and Setters
    public boolean isDrowsy() { return drowsy; }
    public void setDrowsy(boolean drowsy) { this.drowsy = drowsy; }
    
    public boolean isDistracted() { return distracted; }
    public void setDistracted(boolean distracted) { this.distracted = distracted; }
    
    public float getAttentionScore() { return attentionScore; }
    public void setAttentionScore(float attentionScore) { 
        this.attentionScore = Math.max(0, Math.min(100, attentionScore)); 
    }
    
    public String getHeadPose() { return headPose; }
    public void setHeadPose(String headPose) { this.headPose = headPose; }
    
    public boolean isEyesClosed() { return eyesClosed; }
    public void setEyesClosed(boolean eyesClosed) { this.eyesClosed = eyesClosed; }
    
    public boolean isYawning() { return yawning; }
    public void setYawning(boolean yawning) { this.yawning = yawning; }
    
    public boolean isPhoneUsage() { return phoneUsage; }
    public void setPhoneUsage(boolean phoneUsage) { this.phoneUsage = phoneUsage; }
    
    public int getBlinksPerMinute() { return blinksPerMinute; }
    public void setBlinksPerMinute(int blinksPerMinute) { 
        this.blinksPerMinute = blinksPerMinute; 
    }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    /**
     * Check if driver was in unsafe state
     */
    public boolean isUnsafeState() {
        return drowsy || distracted || eyesClosed || 
               phoneUsage || attentionScore < 50;
    }
    
    /**
     * Get severity level of driver state
     * @return "SAFE", "WARNING", "CRITICAL"
     */
    public String getSeverityLevel() {
        if (eyesClosed || phoneUsage || attentionScore < 30) {
            return "CRITICAL";
        } else if (drowsy || distracted || attentionScore < 70) {
            return "WARNING";
        }
        return "SAFE";
    }
}
