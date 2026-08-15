const express = require('express');
const router = express.Router();
const analyticsService = require('../services/analyticsService');
const { authenticateToken } = require('../middleware/auth');

/**
 * Analytics Routes
 * All routes require authentication
 */

/**
 * @route   GET /api/analytics/dashboard
 * @desc    Get comprehensive dashboard summary
 * @access  Private
 */
router.get('/dashboard', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getDashboardSummary({ userId });
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Dashboard error:', error);
    res.status(500).json({ error: 'Failed to fetch dashboard data' });
  }
});

/**
 * @route   GET /api/analytics/trends
 * @desc    Get accident trends over time
 * @access  Private
 */
router.get('/trends', authenticateToken, async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getAccidentTrends({
      userId,
      startDate,
      endDate
    });
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Trends error:', error);
    res.status(500).json({ error: 'Failed to fetch trend data' });
  }
});

/**
 * @route   GET /api/analytics/severity
 * @desc    Get severity-wise statistics
 * @access  Private
 */
router.get('/severity', authenticateToken, async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getSeverityStatistics({
      userId,
      startDate,
      endDate
    });
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Severity stats error:', error);
    res.status(500).json({ error: 'Failed to fetch severity statistics' });
  }
});

/**
 * @route   GET /api/analytics/hotspots
 * @desc    Get accident location hotspots
 * @access  Private
 */
router.get('/hotspots', authenticateToken, async (req, res) => {
  try {
    const { startDate, endDate, radius } = req.query;
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getAccidentHotspots({
      userId,
      startDate,
      endDate,
      radius: radius ? parseFloat(radius) : 0.01
    });
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Hotspots error:', error);
    res.status(500).json({ error: 'Failed to fetch hotspot data' });
  }
});

/**
 * @route   GET /api/analytics/patterns
 * @desc    Get time-based accident patterns
 * @access  Private
 */
router.get('/patterns', authenticateToken, async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getTimePatterns({
      userId,
      startDate,
      endDate
    });
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Patterns error:', error);
    res.status(500).json({ error: 'Failed to fetch pattern data' });
  }
});

/**
 * @route   GET /api/analytics/monthly
 * @desc    Get monthly comparison statistics
 * @access  Private
 */
router.get('/monthly', authenticateToken, async (req, res) => {
  try {
    const userId = req.user.role === 'DRIVER' ? req.user.userId : null;
    
    const result = await analyticsService.getMonthlyComparison(userId);
    
    if (result.success) {
      res.json(result.data);
    } else {
      res.status(500).json({ error: result.error });
    }
  } catch (error) {
    console.error('Monthly comparison error:', error);
    res.status(500).json({ error: 'Failed to fetch monthly data' });
  }
});

module.exports = router;
