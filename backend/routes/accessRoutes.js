const express = require('express');
const router = express.Router();
const accessControlService = require('../services/accessControlService');
const { authenticateToken } = require('../middleware/auth');

/**
 * Access Control Routes
 * For authority/insurance access to incident data
 */

/**
 * @route   POST /api/access/grant
 * @desc    Grant access to an incident
 * @access  Private (Driver only)
 */
router.post('/grant', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'DRIVER') {
      return res.status(403).json({ error: 'Only drivers can grant access' });
    }

    const {
      incidentId,
      grantedToEmail,
      organization,
      role,
      permissions,
      expiresInHours,
      caseNumber,
      requestReason
    } = req.body;

    if (!incidentId || !grantedToEmail || !role) {
      return res.status(400).json({ 
        error: 'Missing required fields: incidentId, grantedToEmail, role' 
      });
    }

    const result = await accessControlService.grantAccess({
      incidentId,
      grantedBy: req.user.userId,
      grantedToEmail,
      organization,
      role,
      permissions,
      expiresInHours,
      caseNumber,
      requestReason
    });

    if (result.success) {
      res.json(result.data);
    } else {
      res.status(400).json({ error: result.error });
    }
  } catch (error) {
    console.error('Grant access error:', error);
    res.status(500).json({ error: 'Failed to grant access' });
  }
});

/**
 * @route   GET /api/access/incident/:token
 * @desc    Get incident data using access token
 * @access  Public (with valid token)
 */
router.get('/incident/:token', async (req, res) => {
  try {
    const { token } = req.params;
    const ipAddress = req.ip || req.connection.remoteAddress;
    const userAgent = req.get('user-agent');

    const result = await accessControlService.getIncidentWithToken(
      token,
      ipAddress,
      userAgent
    );

    if (result.success) {
      res.json(result.data);
    } else {
      res.status(403).json({ error: result.error });
    }
  } catch (error) {
    console.error('Get incident with token error:', error);
    res.status(500).json({ error: 'Failed to retrieve incident' });
  }
});

/**
 * @route   GET /api/access/tokens/:incidentId
 * @desc    Get all access tokens for an incident
 * @access  Private (Driver only)
 */
router.get('/tokens/:incidentId', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'DRIVER') {
      return res.status(403).json({ error: 'Unauthorized' });
    }

    const { incidentId } = req.params;

    const result = await accessControlService.getIncidentAccessTokens(incidentId);

    if (result.success) {
      res.json(result.data);
    } else {
      res.status(400).json({ error: result.error });
    }
  } catch (error) {
    console.error('Get tokens error:', error);
    res.status(500).json({ error: 'Failed to retrieve tokens' });
  }
});

/**
 * @route   POST /api/access/revoke/:tokenId
 * @desc    Revoke an access token
 * @access  Private (Driver only)
 */
router.post('/revoke/:tokenId', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'DRIVER') {
      return res.status(403).json({ error: 'Unauthorized' });
    }

    const { tokenId } = req.params;
    const { reason } = req.body;

    const result = await accessControlService.revokeAccess(
      tokenId,
      reason || 'Revoked by owner',
      req.user.userId
    );

    if (result.success) {
      res.json({ message: result.message });
    } else {
      res.status(400).json({ error: result.error });
    }
  } catch (error) {
    console.error('Revoke token error:', error);
    res.status(500).json({ error: 'Failed to revoke token' });
  }
});

/**
 * @route   GET /api/access/audit/:incidentId
 * @desc    Get access audit log for an incident
 * @access  Private (Driver only)
 */
router.get('/audit/:incidentId', authenticateToken, async (req, res) => {
  try {
    if (req.user.role !== 'DRIVER') {
      return res.status(403).json({ error: 'Unauthorized' });
    }

    const { incidentId } = req.params;

    const result = await accessControlService.getAccessAuditLog(incidentId);

    if (result.success) {
      res.json(result.data);
    } else {
      res.status(400).json({ error: result.error });
    }
  } catch (error) {
    console.error('Get audit log error:', error);
    res.status(500).json({ error: 'Failed to retrieve audit log' });
  }
});

module.exports = router;
