package com.example.smartfleetx.model;

import java.io.Serializable;

/**
 * Model class representing vehicle data at the time of an incident
 * Captures OBD-II data and sensor information
 */
public class VehicleData implements Serializable {
    private float speed;              // km/h
    private int rpm;                  // Engine RPM
    private float fuelLevel;          // Percentage (0-100)
    private float coolantTemp;        // Celsius
    private float engineLoad;         // Percentage (0-100)
    private boolean hardBrake;        // Hard braking detected
    private boolean rapidAcceleration;// Rapid acceleration detected
    private float gForce;             // G-force at incident
    private String[] dtcCodes;        // Diagnostic Trouble Codes
    private long timestamp;           // When this data was captured
    
    public VehicleData() {
        this.dtcCodes = new String[0];
    }
    
    // Getters and Setters
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    
    public int getRpm() { return rpm; }
    public void setRpm(int rpm) { this.rpm = rpm; }
    
    public float getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(float fuelLevel) { this.fuelLevel = fuelLevel; }
    
    public float getCoolantTemp() { return coolantTemp; }
    public void setCoolantTemp(float coolantTemp) { this.coolantTemp = coolantTemp; }
    
    public float getEngineLoad() { return engineLoad; }
    public void setEngineLoad(float engineLoad) { this.engineLoad = engineLoad; }
    
    public boolean isHardBrake() { return hardBrake; }
    public void setHardBrake(boolean hardBrake) { this.hardBrake = hardBrake; }
    
    public boolean isRapidAcceleration() { return rapidAcceleration; }
    public void setRapidAcceleration(boolean rapidAcceleration) { 
        this.rapidAcceleration = rapidAcceleration; 
    }
    
    public float getgForce() { return gForce; }
    public void setgForce(float gForce) { this.gForce = gForce; }
    
    public String[] getDtcCodes() { return dtcCodes; }
    public void setDtcCodes(String[] dtcCodes) { this.dtcCodes = dtcCodes; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    /**
     * Check if vehicle was in abnormal state
     */
    public boolean isAbnormalState() {
        return hardBrake || rapidAcceleration || 
               (dtcCodes != null && dtcCodes.length > 0) ||
               coolantTemp > 105 || // Overheating
               engineLoad > 95;     // Engine under stress
    }
}