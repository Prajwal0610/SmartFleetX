package com.example.smartfleetx.utils;

import java.util.List;

/**
 * AccidentConfidenceScorer - Calculates confidence score for accident detection
 * Multi-factor analysis including:
 * - G-force magnitude
 * - Speed change
 * - Impact pattern consistency
 * - Gyroscope data (rotational impact)
 */
public class AccidentConfidenceScorer {

    private static final String TAG = "ConfidenceScorer";

    // Weighting factors
    private static final float WEIGHT_GFORCE = 0.40f;
    private static final float WEIGHT_SPEED = 0.25f;
    private static final float WEIGHT_PATTERN = 0.20f;
    private static final float WEIGHT_ROTATION = 0.15f;

    // Thresholds for scoring
    private static final float GFORCE_MAX = 10.0f;  // 10G = 100% score
    private static final float SPEED_MAX = 100.0f;   // 100 km/h = 100% score
    private static final float GYRO_MAX = 5.0f;      // 5 rad/s = 100% score

    /**
     * Calculate overall confidence score (0-100)
     *
     * @param gForce Current G-force magnitude
     * @param gyroData Gyroscope data [x, y, z]
     * @param currentSpeed Current speed in km/h
     * @param recentGForces Recent G-force buffer for pattern analysis
     * @return Confidence score from 0 to 100
     */
    public int calculateConfidence(float gForce, float[] gyroData, float currentSpeed, 
                                   List<Float> recentGForces) {
        
        // Factor 1: G-force magnitude score
        float gForceScore = Math.min(gForce / GFORCE_MAX, 1.0f) * 100;

        // Factor 2: Speed score (higher speed = higher confidence)
        float speedScore = Math.min(currentSpeed / SPEED_MAX, 1.0f) * 100;

        // Factor 3: Impact pattern consistency
        float patternScore = calculatePatternScore(recentGForces);

        // Factor 4: Rotational impact (gyroscope)
        float rotationScore = calculateRotationScore(gyroData);

        // Weighted average
        float totalScore = 
            (gForceScore * WEIGHT_GFORCE) +
            (speedScore * WEIGHT_SPEED) +
            (patternScore * WEIGHT_PATTERN) +
            (rotationScore * WEIGHT_ROTATION);

        // Ensure score is within bounds
        int confidence = Math.round(Math.max(0, Math.min(100, totalScore)));

        return confidence;
    }

    /**
     * Analyze impact pattern from recent G-force buffer
     * Sudden spikes indicate real accidents, gradual changes are likely false positives
     */
    private float calculatePatternScore(List<Float> recentGForces) {
        if (recentGForces == null || recentGForces.size() < 3) {
            return 50; // Default score if not enough data
        }

        // Calculate rate of change
        float maxChange = 0;
        for (int i = 1; i < recentGForces.size(); i++) {
            float change = Math.abs(recentGForces.get(i) - recentGForces.get(i - 1));
            maxChange = Math.max(maxChange, change);
        }

        // Sudden changes (> 2G in one sample) indicate real impact
        // Gradual changes are likely speed bumps
        float suddennessScore = Math.min(maxChange / 2.0f, 1.0f) * 100;

        // Check for spike concentration (real accidents have focused spikes)
        float peakValue = 0;
        for (float g : recentGForces) {
            peakValue = Math.max(peakValue, g);
        }

        float avgValue = 0;
        for (float g : recentGForces) {
            avgValue += g;
        }
        avgValue /= recentGForces.size();

        // High peak-to-average ratio indicates concentrated impact
        float concentrationRatio = avgValue > 0 ? (peakValue / avgValue) : 1;
        float concentrationScore = Math.min(concentrationRatio / 3.0f, 1.0f) * 100;

        // Combine suddenness and concentration
        return (suddennessScore * 0.6f + concentrationScore * 0.4f);
    }

    /**
     * Calculate rotation score from gyroscope data
     * Real accidents typically involve significant vehicle rotation
     */
    private float calculateRotationScore(float[] gyroData) {
        if (gyroData == null || gyroData.length != 3) {
            return 50; // Default if no gyroscope data
        }

        // Calculate magnitude of angular velocity
        float magnitude = (float) Math.sqrt(
            gyroData[0] * gyroData[0] +
            gyroData[1] * gyroData[1] +
            gyroData[2] * gyroData[2]
        );

        // Score based on rotation magnitude
        float score = Math.min(magnitude / GYRO_MAX, 1.0f) * 100;

        return score;
    }

    /**
     * Calculate severity-specific confidence adjustment
     * Higher severity accidents should have higher base confidence
     */
    public int adjustConfidenceBySeverity(int baseConfidence, String severity) {
        float multiplier = 1.0f;

        switch (severity) {
            case "SEVERE":
                multiplier = 1.15f; // Boost confidence for severe impacts
                break;
            case "MODERATE":
                multiplier = 1.05f;
                break;
            case "MINOR":
                multiplier = 0.95f; // Slightly reduce for minor (more likely false positive)
                break;
        }

        int adjusted = Math.round(baseConfidence * multiplier);
        return Math.min(100, adjusted);
    }

    /**
     * Calculate confidence for severity classification
     * Returns how confident we are in the severity level
     */
    public float calculateSeverityConfidence(float gForce, float[] gyroData) {
        // Clear-cut cases have high confidence
        if (gForce >= 8.0f || gForce <= 3.0f) {
            return 0.95f; // 95% confident
        }

        // Border cases have lower confidence
        // e.g., 3.9G could be MINOR or MODERATE
        if ((gForce >= 3.8f && gForce <= 4.2f) || (gForce >= 5.8f && gForce <= 6.2f)) {
            return 0.70f; // 70% confident
        }

        // Default confidence for mid-range values
        return 0.85f; // 85% confident
    }
}
