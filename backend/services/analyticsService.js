const mongoose = require('mongoose');
const Incident = require('../models/Incident');

/**
 * Analytics Service - Accident trend analysis and statistics
 * Provides data aggregation for dashboard visualization
 */
class AnalyticsService {
  
  /**
   * Get accident trend statistics
   * @param {Object} filters - Date range, severity, etc.
   * @returns {Object} Trend statistics
   */
  async getAccidentTrends(filters = {}) {
    const { startDate, endDate, userId } = filters;
    
    const matchStage = {};
    
    if (userId) {
      matchStage.userId = new mongoose.Types.ObjectId(userId);
    }
    
    if (startDate && endDate) {
      matchStage.timestamp = {
        $gte: new Date(startDate).getTime(),
        $lte: new Date(endDate).getTime()
      };
    }

    try {
      // Aggregate trends by day
      const dailyTrends = await Incident.aggregate([
        { $match: matchStage },
        {
          $group: {
            _id: {
              $dateToString: {
                format: '%Y-%m-%d',
                date: { $toDate: '$timestamp' }
              }
            },
            count: { $sum: 1 },
            minorCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'MINOR'] }, 1, 0] }
            },
            moderateCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'MODERATE'] }, 1, 0] }
            },
            severeCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'SEVERE'] }, 1, 0] }
            },
            avgImpactForce: { $avg: '$impactForce' }
          }
        },
        { $sort: { _id: 1 } }
      ]);

      return {
        success: true,
        data: dailyTrends
      };
    } catch (error) {
      console.error('Error getting accident trends:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get severity-wise statistics
   */
  async getSeverityStatistics(filters = {}) {
    const { startDate, endDate, userId } = filters;
    
    const matchStage = {};
    if (userId) matchStage.userId = new mongoose.Types.ObjectId(userId);
    if (startDate && endDate) {
      matchStage.timestamp = {
        $gte: new Date(startDate).getTime(),
        $lte: new Date(endDate).getTime()
      };
    }

    try {
      const stats = await Incident.aggregate([
        { $match: matchStage },
        {
          $group: {
            _id: '$severity',
            count: { $sum: 1 },
            avgConfidenceScore: { $avg: '$confidenceScore' },
            avgImpactForce: { $avg: '$impactForce' },
            avgSeverityScore: { $avg: '$severityScore' }
          }
        }
      ]);

      // Calculate totals and percentages
      const total = stats.reduce((sum, item) => sum + item.count, 0);
      
      const severityData = {
        MINOR: { count: 0, percentage: 0, avgImpact: 0 },
        MODERATE: { count: 0, percentage: 0, avgImpact: 0 },
        SEVERE: { count: 0, percentage: 0, avgImpact: 0 }
      };

      stats.forEach(stat => {
        const severity = stat._id || 'MINOR';
        severityData[severity] = {
          count: stat.count,
          percentage: total > 0 ? ((stat.count / total) * 100).toFixed(2) : 0,
          avgImpact: stat.avgImpactForce ? stat.avgImpactForce.toFixed(2) : 0,
          avgConfidence: stat.avgConfidenceScore ? stat.avgConfidenceScore.toFixed(2) : 0,
          avgSeverityScore: stat.avgSeverityScore ? stat.avgSeverityScore.toFixed(2) : 0
        };
      });

      return {
        success: true,
        data: {
          total,
          byPriority: severityData
        }
      };
    } catch (error) {
      console.error('Error getting severity statistics:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get location-based accident hotspots
   */
  async getAccidentHotspots(filters = {}) {
    const { startDate, endDate, userId, radius = 0.01 } = filters; // ~1km radius
    
    const matchStage = {};
    if (userId) matchStage.userId = new mongoose.Types.ObjectId(userId);
    if (startDate && endDate) {
      matchStage.timestamp = {
        $gte: new Date(startDate).getTime(),
        $lte: new Date(endDate).getTime()
      };
    }

    try {
      // Group incidents by approximate location (rounded to create hotspot zones)
      const hotspots = await Incident.aggregate([
        { $match: matchStage },
        {
          $project: {
            latRounded: { 
              $round: [{ $divide: ['$latitude', radius] }, 0] 
            },
            lngRounded: {
              $round: [{ $divide: ['$longitude', radius] }, 0]
            },
            severity: 1,
            impactForce: 1,
            latitude: 1,
            longitude: 1
          }
        },
        {
          $group: {
            _id: {
              lat: '$latRounded',
              lng: '$lngRounded'
            },
            count: { $sum: 1 },
            avgLat: { $avg: '$latitude' },
            avgLng: { $avg: '$longitude' },
            avgImpactForce: { $avg: '$impactForce' },
            severeCoun: {
              $sum: { $cond: [{ $eq: ['$severity', 'SEVERE'] }, 1, 0] }
            },
            moderateCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'MODERATE'] }, 1, 0] }
            },
            minorCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'MINOR'] }, 1, 0] }
            }
          }
        },
        { $sort: { count: -1 } },
        { $limit: 50 } // Top 50 hotspots
      ]);

      // Format for map display
      const formattedHotspots = hotspots.map(spot => ({
        latitude: spot.avgLat,
        longitude: spot.avgLng,
        count: spot.count,
        severity: {
          severe: spot.severeCount,
          moderate: spot.moderateCount,
          minor: spot.minorCount
        },
        avgImpact: spot.avgImpactForce ? spot.avgImpactForce.toFixed(2) : 0,
        radius: Math.min(spot.count * 100, 1000) // Visual radius for map
      }));

      return {
        success: true,
        data: formattedHotspots
      };
    } catch (error) {
      console.error('Error getting accident hotspots:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get time-based pattern detection (hour of day, day of week)
   */
  async getTimePatterns(filters = {}) {
    const { startDate, endDate, userId } = filters;
    
    const matchStage = {};
    if (userId) matchStage.userId = new mongoose.Types.ObjectId(userId);
    if (startDate && endDate) {
      matchStage.timestamp = {
        $gte: new Date(startDate).getTime(),
        $lte: new Date(endDate).getTime()
      };
    }

    try {
      // Hour of day pattern
      const hourlyPattern = await Incident.aggregate([
        { $match: matchStage },
        {
          $project: {
            hour: {
              $hour: { $toDate: '$timestamp' }
            },
            severity: 1
          }
        },
        {
          $group: {
            _id: '$hour',
            count: { $sum: 1 },
            severeCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'SEVERE'] }, 1, 0] }
            }
          }
        },
        { $sort: { _id: 1 } }
      ]);

      // Day of week pattern
      const dayPattern = await Incident.aggregate([
        { $match: matchStage },
        {
          $project: {
            dayOfWeek: {
              $dayOfWeek: { $toDate: '$timestamp' }
            },
            severity: 1
          }
        },
        {
          $group: {
            _id: '$dayOfWeek',
            count: { $sum: 1 },
            severeCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'SEVERE'] }, 1, 0] }
            }
          }
        },
        { $sort: { _id: 1 } }
      ]);

      const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
      const formattedDayPattern = dayPattern.map(day => ({
        day: dayNames[day._id - 1],
        dayNumber: day._id,
        count: day.count,
        severeCount: day.severeCount
      }));

      return {
        success: true,
        data: {
          hourly: hourlyPattern,
          daily: formattedDayPattern
        }
      };
    } catch (error) {
      console.error('Error getting time patterns:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get comprehensive dashboard summary
   */
  async getDashboardSummary(filters = {}) {
    const { userId } = filters;

    try {
      const matchStage = userId ? { userId: new mongoose.Types.ObjectId(userId) } : {};

      // Total incidents
      const totalIncidents = await Incident.countDocuments(matchStage);

      // Last 30 days incidents
      const thirtyDaysAgo = Date.now() - (30 * 24 * 60 * 60 * 1000);
      const recentIncidents = await Incident.countDocuments({
        ...matchStage,
        timestamp: { $gte: thirtyDaysAgo }
      });

      // Severity breakdown
      const severityBreakdown = await Incident.aggregate([
        { $match: matchStage },
        {
          $group: {
            _id: '$severity',
            count: { $sum: 1 }
          }
        }
      ]);

      // Average impact force
      const avgImpactResult = await Incident.aggregate([
        { $match: matchStage },
        {
          $group: {
            _id: null,
            avgImpact: { $avg: '$impactForce' },
            maxImpact: { $max: '$impactForce' },
            avgConfidence: { $avg: '$confidenceScore' }
          }
        }
      ]);

      // Recent severe incidents
      const recentSevere = await Incident.find({
        ...matchStage,
        severity: 'SEVERE'
      })
      .sort({ timestamp: -1 })
      .limit(5)
      .select('id timestamp latitude longitude impactForce address');

      return {
        success: true,
        data: {
          summary: {
            totalIncidents,
            recentIncidents,
            avgImpactForce: avgImpactResult[0]?.avgImpact?.toFixed(2) || 0,
            maxImpactForce: avgImpactResult[0]?.maxImpact?.toFixed(2) || 0,
            avgConfidence: avgImpactResult[0]?.avgConfidence?.toFixed(2) || 0
          },
          severityBreakdown: severityBreakdown.reduce((acc, item) => {
            acc[item._id || 'MINOR'] = item.count;
            return acc;
          }, { MINOR: 0, MODERATE: 0, SEVERE: 0 }),
          recentSevereIncidents: recentSevere
        }
      };
    } catch (error) {
      console.error('Error getting dashboard summary:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get monthly comparison statistics
   */
  async getMonthlyComparison(userId = null) {
    try {
      const matchStage = userId ? { userId: new mongoose.Types.ObjectId(userId) } : {};

      const monthlyData = await Incident.aggregate([
        { $match: matchStage },
        {
          $project: {
            year: { $year: { $toDate: '$timestamp' } },
            month: { $month: { $toDate: '$timestamp' } },
            severity: 1,
            impactForce: 1
          }
        },
        {
          $group: {
            _id: {
              year: '$year',
              month: '$month'
            },
            count: { $sum: 1 },
            avgImpact: { $avg: '$impactForce' },
            severeCount: {
              $sum: { $cond: [{ $eq: ['$severity', 'SEVERE'] }, 1, 0] }
            }
          }
        },
        { $sort: { '_id.year': -1, '_id.month': -1 } },
        { $limit: 12 } // Last 12 months
      ]);

      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 
                         'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      
      const formattedData = monthlyData.map(item => ({
        month: monthNames[item._id.month - 1],
        year: item._id.year,
        count: item.count,
        avgImpact: item.avgImpact ? item.avgImpact.toFixed(2) : 0,
        severeCount: item.severeCount
      })).reverse();

      return {
        success: true,
        data: formattedData
      };
    } catch (error) {
      console.error('Error getting monthly comparison:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }
}

module.exports = new AnalyticsService();
