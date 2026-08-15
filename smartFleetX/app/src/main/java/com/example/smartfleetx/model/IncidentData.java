package com.example.smartfleetx.model;

import android.location.Location;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Incident model with comprehensive data for incident analysis
 * Includes video, location, vehicle data, and driver state
 */
public class IncidentData implements Serializable {
    private String id;
    private long timestamp;
    private String type;              // "ACCIDENT", "HARD_BRAKE", "RAPID_ACCELERATION", "MANUAL"
    private String severity;          // "MINOR", "MODERATE", "SEVERE"
    
    // Severity metrics (new)
    private int severityScore;        // 0-100
    private float severityConfidence; // 0-1
    private float impactForce;        // G-force magnitude
    private float deltaV;             // Speed change in m/s
    private int confidenceScore;      // Overall accident confidence 0-100
    
    // Location data
    private double latitude;
    private double longitude;
    private String address;
    private float bearing;            // Direction vehicle was heading
    
    // Media files
    private String videoPath;         // Path to dash cam video
    private String driverVideoPath;   // Path to driver monitoring video (if available)
    private String thumbnailPath;     // Thumbnail image path
    
    // Data at incident time
    private VehicleData vehicleData;
    private DriverState driverState;
    
    // Sensor logs (new)
    private List<String> sensorLogIds; // References to sensor logs
    
    // Analysis results
    private String faultDetermination;  // "DRIVER_FAULT", "OTHER_PARTY", "UNCLEAR", "NO_FAULT"
    private String analysisNotes;       // AI/Manual analysis notes
    private boolean reportGenerated;
    private String reportPath;          // Path to PDF report
    
    // Evidence integrity (new)
    private String dataHash;            // SHA-256 hash
    private float integrityScore;       // 0-1
    private long verifiedAt;
    private String integrityStatus;     // "VERIFIED", "UNVERIFIED", "TAMPERED"
    
    // Insurance data
    private boolean insuranceClaimFiled;
    private String claimNumber;
    
    // Timestamps
    private long createdAt;
    private long analyzedAt;
    
    public IncidentData() {
        this.id = generateId();
        this.timestamp = System.currentTimeMillis();
        this.createdAt = this.timestamp;
        this.severity = "MINOR";
        this.type = "MANUAL";
    }
    
    private String generateId() {
        return "INC_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public float getBearing() { return bearing; }
    public void setBearing(float bearing) { this.bearing = bearing; }
    
    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
    
    public String getDriverVideoPath() { return driverVideoPath; }
    public void setDriverVideoPath(String driverVideoPath) { 
        this.driverVideoPath = driverVideoPath; 
    }
    
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
    
    public VehicleData getVehicleData() { return vehicleData; }
    public void setVehicleData(VehicleData vehicleData) { this.vehicleData = vehicleData; }
    
    public DriverState getDriverState() { return driverState; }
    public void setDriverState(DriverState driverState) { this.driverState = driverState; }
    
    public String getFaultDetermination() { return faultDetermination; }
    public void setFaultDetermination(String faultDetermination) { 
        this.faultDetermination = faultDetermination; 
    }
    
    public String getAnalysisNotes() { return analysisNotes; }
    public void setAnalysisNotes(String analysisNotes) { this.analysisNotes = analysisNotes; }
    
    public boolean isReportGenerated() { return reportGenerated; }
    public void setReportGenerated(boolean reportGenerated) { 
        this.reportGenerated = reportGenerated; 
    }
    
    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }
    
    public boolean isInsuranceClaimFiled() { return insuranceClaimFiled; }
    public void setInsuranceClaimFiled(boolean insuranceClaimFiled) { 
        this.insuranceClaimFiled = insuranceClaimFiled; 
    }
    
    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(long analyzedAt) { this.analyzedAt = analyzedAt; }
    
    /**
     * Get location as Location object
     */
    public Location getLocation() {
        Location location = new Location("");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setBearing(bearing);
        return location;
    }
    
    /**
     * Set location from Location object
     */
    public void setLocation(Location location) {
        if (location != null) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
            this.bearing = location.getBearing();
        }
    }
    
    // New getters and setters for severity metrics
    public int getSeverityScore() { return severityScore; }
    public void setSeverityScore(int severityScore) { this.severityScore = severityScore; }
    
    public float getSeverityConfidence() { return severityConfidence; }
    public void setSeverityConfidence(float severityConfidence) { 
        this.severityConfidence = severityConfidence; 
    }
    
    public float getImpactForce() { return impactForce; }
    public void setImpactForce(float impactForce) { this.impactForce = impactForce; }
    
    public float getDeltaV() { return deltaV; }
    public void setDeltaV(float deltaV) { this.deltaV = deltaV; }
    
    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }
    
    // Sensor logs
    public List<String> getSensorLogIds() { 
        if (sensorLogIds == null) {
            sensorLogIds = new ArrayList<>();
        }
        return sensorLogIds; 
    }
    public void setSensorLogIds(List<String> sensorLogIds) { 
        this.sensorLogIds = sensorLogIds; 
    }
    
    // Evidence integrity
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    
    public float getIntegrityScore() { return integrityScore; }
    public void setIntegrityScore(float integrityScore) { this.integrityScore = integrityScore; }
    
    public long getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(long verifiedAt) { this.verifiedAt = verifiedAt; }
    
    public String getIntegrityStatus() { return integrityStatus; }
    public void setIntegrityStatus(String integrityStatus) { 
        this.integrityStatus = integrityStatus; 
    }
}
