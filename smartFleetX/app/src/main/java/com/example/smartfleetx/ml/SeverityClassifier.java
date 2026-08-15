package com.example.smartfleetx.ml;

import com.example.smartfleetx.model.SensorLog;

import java.util.List;

/**
 * SeverityClassifier - Classifies accident severity based on multiple factors
 * Three-tier classification: MINOR, MODERATE, SEVERE
 * 
 * Classification Criteria:
 * - MINOR: <4G, low speed change, minor damage expected
 * - MODERATE: 4-8G, medium speed change, moderate damage expected
 * - SEVERE: >8G, high speed change, serious damage expected
 */
public class SeverityClassifier {

    private static final String TAG = "SeverityClassifier";

    // G-force thresholds
    private static final float THRESHOLD_MINOR = 4.0f;
    private static final float THRESHOLD_MODERATE = 6.0f;
    private static final float THRESHOLD_SEVERE = 8.0f;

    // Speed change thresholds (delta-V in m/s)
    private static final float DELTA_V_MINOR = 5.0f;      // ~18 km/h
    private static final float DELTA_V_MODERATE = 10.0f;  // ~36 km/h
    private static final float DELTA_V_SEVERE = 15.0f;    // ~54 km/h

    /**
     * Classify accident severity
     * 
     * @param gForceMagnitude Peak G-force magnitude
     * @param gyroData Gyroscope data [x, y, z]
     * @param preSpeed Speed before impact (m/s)
     * @param postSpeed Speed after impact (m/s)
     * @return Severity level: "MINOR", "MODERATE", or "SEVERE"
     */
    public String classifySeverity(float gForceMagnitude, float[] gyroData, 
                                   float preSpeed, float postSpeed) {
        
        // Calculate delta-V (speed change)
        float deltaV = Math.abs(preSpeed - postSpeed);

        // Calculate severity score (0-100)
        int severityScore = calculateSeverityScore(gForceMagnitude, deltaV, gyroData);

        // Classify based on primary factor (G-force) with delta-V adjustment
        String severity;
        if (gForceMagnitude >= THRESHOLD_SEVERE || deltaV >= DELTA_V_SEVERE) {
            severity = "SEVERE";
        } else if (gForceMagnitude >= THRESHOLD_MODERATE || deltaV >= DELTA_V_MODERATE) {
            severity = "MODERATE";
        } else {
            severity = "MINOR";
        }

        // Adjust based on severity score
        if (severityScore >= 80) {
            severity = "SEVERE";
        } else if (severityScore >= 50 && severity.equals("MINOR")) {
            severity = "MODERATE";
        }

        return severity;
    }

    /**
     * Calculate severity score (0-100)
     * Higher score = more severe
     */
    public int calculateSeverityScore(float gForceMagnitude, float deltaV, float[] gyroData) {
        // Factor 1: G-force score (50% weight)
        float gForceScore = Math.min(gForceMagnitude / 10.0f, 1.0f) * 100;

        // Factor 2: Delta-V score (30% weight)
        float deltaVScore = Math.min(deltaV / 20.0f, 1.0f) * 100;

        // Factor 3: Rotational impact score (20% weight)
        float rotationScore = 0;
        if (gyroData != null && gyroData.length == 3) {
            float gyroMagnitude = (float) Math.sqrt(
                gyroData[0] * gyroData[0] +
                gyroData[1] * gyroData[1] +
                gyroData[2] * gyroData[2]
            );
            rotationScore = Math.min(gyroMagnitude / 5.0f, 1.0f) * 100;
        }

        // Weighted average
        float totalScore = (gForceScore * 0.5f) + (deltaVScore * 0.3f) + (rotationScore * 0.2f);

        return Math.round(Math.max(0, Math.min(100, totalScore)));
    }

    /**
     * Calculate severity confidence (0-1)
     * How confident are we in the severity classification?
     */
    public float calculateSeverityConfidence(float gForceMagnitude, float deltaV) {
        // Clear-cut cases have high confidence
        if (gForceMagnitude >= THRESHOLD_SEVERE + 2.0f) {
            return 0.95f; // Very confident it's SEVERE
        }
        if (gForceMagnitude <= THRESHOLD_MINOR - 1.0f) {
            return 0.95f; // Very confident it's MINOR
        }

        // Border cases have lower confidence
        // Check if G-force is near threshold boundaries
        boolean nearMinorBoundary = Math.abs(gForceMagnitude - THRESHOLD_MINOR) < 0.5f;
        boolean nearModerateBoundary = Math.abs(gForceMagnitude - THRESHOLD_MODERATE) < 0.5f;
        boolean nearSevereBoundary = Math.abs(gForceMagnitude - THRESHOLD_SEVERE) < 0.5f;

        if (nearMinorBoundary || nearModerateBoundary || nearSevereBoundary) {
            // Use delta-V to increase confidence
            if (deltaV > DELTA_V_MODERATE) {
                return 0.75f; // Delta-V confirms higher severity
            }
            return 0.65f; // Borderline case
        }

        // Mid-range values have good confidence
        return 0.85f;
    }

    /**
     * Estimate vehicle damage level
     */
    public String estimateDamageLevel(String severity, float gForceMagnitude) {
        switch (severity) {
            case "SEVERE":
                return "Severe damage expected. Vehicle may not be drivable. " +
                       "Airbags likely deployed. Immediate professional assessment required.";
            
            case "MODERATE":
                return "Moderate damage expected. Body damage likely. " +
                       "Check for airbag deployment. Professional inspection recommended.";
            
            case "MINOR":
                if (gForceMagnitude > 3.0f) {
                    return "Minor to moderate damage possible. Inspect bumpers, fenders. " +
                           "Vehicle likely drivable but inspection recommended.";
                } else {
                    return "Minimal damage expected. Visual inspection recommended.";
                }
            
            default:
                return "Unknown damage level";
        }
    }

    /**
     * Analyze impact pattern from sensor logs
     */
    public ImpactAnalysis analyzeImpactPattern(List<SensorLog> sensorLogs) {
        if (sensorLogs == null || sensorLogs.isEmpty()) {
            return new ImpactAnalysis("UNKNOWN", 0, 0);
        }

        // Find peak G-force
        float peakGForce = 0;
        long peakTime = 0;
        
        for (SensorLog log : sensorLogs) {
            if (log.gForceMagnitude > peakGForce) {
                peakGForce = log.gForceMagnitude;
                peakTime = log.timestamp;
            }
        }

        // Determine impact direction based on peak G-force components
        SensorLog peakLog = null;
        for (SensorLog log : sensorLogs) {
            if (log.timestamp == peakTime) {
                peakLog = log;
                break;
            }
        }

        String impactDirection = "UNKNOWN";
        if (peakLog != null) {
            float maxAxis = Math.max(Math.abs(peakLog.gForceX), 
                            Math.max(Math.abs(peakLog.gForceY), Math.abs(peakLog.gForceZ)));
            
            if (Math.abs(peakLog.gForceX) == maxAxis) {
                impactDirection = peakLog.gForceX > 0 ? "LATERAL_RIGHT" : "LATERAL_LEFT";
            } else if (Math.abs(peakLog.gForceY) == maxAxis) {
                impactDirection = peakLog.gForceY > 0 ? "FRONT" : "REAR";
            } else {
                impactDirection = peakLog.gForceZ > 0 ? "ROLLOVER_UP" : "ROLLOVER_DOWN";
            }
        }

        // Calculate impact duration (time above 50% of peak)
        long impactStartTime = peakTime;
        long impactEndTime = peakTime;
        float threshold = peakGForce * 0.5f;

        for (SensorLog log : sensorLogs) {
            if (log.gForceMagnitude >= threshold) {
                if (log.timestamp < impactStartTime) {
                    impactStartTime = log.timestamp;
                }
                if (log.timestamp > impactEndTime) {
                    impactEndTime = log.timestamp;
                }
            }
        }

        long durationMs = impactEndTime - impactStartTime;

        return new ImpactAnalysis(impactDirection, peakGForce, durationMs);
    }

    /**
     * Impact analysis result
     */
    public static class ImpactAnalysis {
        public String direction;     // FRONT, REAR, LATERAL_LEFT, LATERAL_RIGHT, ROLLOVER
        public float peakGForce;
        public long durationMs;

        public ImpactAnalysis(String direction, float peakGForce, long durationMs) {
            this.direction = direction;
            this.peakGForce = peakGForce;
            this.durationMs = durationMs;
        }

        @Override
        public String toString() {
            return String.format("Impact: %s, Peak: %.2fG, Duration: %dms",
                direction, peakGForce, durationMs);
        }
    }

    /**
     * Get severity description for UI display
     */
    public String getSeverityDescription(String severity) {
        switch (severity) {
            case "SEVERE":
                return "⚠️ SEVERE ACCIDENT\nEmergency services may be required";
            case "MODERATE":
                return "⚠️ MODERATE ACCIDENT\nSeek medical attention if injured";
            case "MINOR":
                return "⚠️ MINOR INCIDENT\nCheck for injuries and damage";
            default:
                return "Unknown severity";
        }
    }

    /**
     * Get recommended actions based on severity
     */
    public String[] getRecommendedActions(String severity) {
        switch (severity) {
            case "SEVERE":
                return new String[]{
                    "Call emergency services (911/112) immediately",
                    "Do not move unless in immediate danger",
                    "Turn off engine if possible",
                    "Check for injuries - do not remove helmet if applicable",
                    "Emergency contacts have been notified",
                    "Document scene with photos if safe"
                };
            
            case "MODERATE":
                return new String[]{
                    "Check yourself and passengers for injuries",
                    "Move to safe location if vehicle is drivable",
                    "Call police if required by law",
                    "Exchange information with other parties",
                    "Document damage with photos",
                    "Contact insurance company"
                };
            
            case "MINOR":
                return new String[]{
                    "Inspect vehicle for damage",
                    "Exchange information if other party involved",
                    "Take photos of any damage",
                    "File police report if required",
                    "Contact insurance if damage exceeds deductible"
                };
            
            default:
                return new String[]{"Assess situation and take appropriate action"};
        }
    }
}
