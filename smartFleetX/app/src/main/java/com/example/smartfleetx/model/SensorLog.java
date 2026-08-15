package com.example.smartfleetx.model;

import java.io.Serializable;

/**
 * SensorLog - Snapshot of sensor data at a specific point in time
 * Used for pre-crash and post-crash data logging
 */
public class SensorLog implements Serializable {
    
    // Timestamp
    public long timestamp;
    
    // G-force data (normalized to G units)
    public float gForceX;
    public float gForceY;
    public float gForceZ;
    public float gForceMagnitude;
    
    // Gyroscope data (rad/s)
    public float gyroX;
    public float gyroY;
    public float gyroZ;
    
    // Speed and location
    public float speed;          // km/h
    public double latitude;
    public double longitude;
    
    // Optional vehicle data (if available from OBD)
    public Float rpm;
    public Float throttlePosition;
    public Float brakeStatus;
    
    public SensorLog() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SensorLog(long timestamp) {
        this.timestamp = timestamp;
    }
    
    // Getters and setters
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public float getGForceX() {
        return gForceX;
    }
    
    public void setGForceX(float gForceX) {
        this.gForceX = gForceX;
    }
    
    public float getGForceY() {
        return gForceY;
    }
    
    public void setGForceY(float gForceY) {
        this.gForceY = gForceY;
    }
    
    public float getGForceZ() {
        return gForceZ;
    }
    
    public void setGForceZ(float gForceZ) {
        this.gForceZ = gForceZ;
    }
    
    public float getGForceMagnitude() {
        return gForceMagnitude;
    }
    
    public void setGForceMagnitude(float gForceMagnitude) {
        this.gForceMagnitude = gForceMagnitude;
    }
    
    public float getGyroX() {
        return gyroX;
    }
    
    public void setGyroX(float gyroX) {
        this.gyroX = gyroX;
    }
    
    public float getGyroY() {
        return gyroY;
    }
    
    public void setGyroY(float gyroY) {
        this.gyroY = gyroY;
    }
    
    public float getGyroZ() {
        return gyroZ;
    }
    
    public void setGyroZ(float gyroZ) {
        this.gyroZ = gyroZ;
    }
    
    public float getSpeed() {
        return speed;
    }
    
    public void setSpeed(float speed) {
        this.speed = speed;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public Float getRpm() {
        return rpm;
    }
    
    public void setRpm(Float rpm) {
        this.rpm = rpm;
    }
    
    public Float getThrottlePosition() {
        return throttlePosition;
    }
    
    public void setThrottlePosition(Float throttlePosition) {
        this.throttlePosition = throttlePosition;
    }
    
    public Float getBrakeStatus() {
        return brakeStatus;
    }
    
    public void setBrakeStatus(Float brakeStatus) {
        this.brakeStatus = brakeStatus;
    }
    
    @Override
    public String toString() {
        return String.format(
            "SensorLog{time=%d, gForce=%.2f, speed=%.1f, lat=%.6f, lon=%.6f}",
            timestamp, gForceMagnitude, speed, latitude, longitude
        );
    }
}
