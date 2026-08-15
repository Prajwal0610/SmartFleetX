const crypto = require('crypto');
const AccessToken = require('../models/AccessToken');
const Incident = require('../models/Incident');

/**
 * Access Control Service
 * Manages authority/insurance access to incident data
 */
class AccessControlService {

  /**
   * Generate secure access token
   */
  generateToken() {
    return crypto.randomBytes(32).toString('hex');
  }

  /**
   * Grant access to an incident
   * @param {Object} params - Grant parameters
   * @returns {Object} Access token details
   */
  async grantAccess(params) {
    const {
      incidentId,
      grantedBy,
      grantedToEmail,
      organization,
      role,
      permissions,
      expiresInHours = 72, // Default 3 days
      caseNumber,
      requestReason
    } = params;

    try {
      // Verify incident exists
      const incident = await Incident.findById(incidentId);
      if (!incident) {
        return {
          success: false,
          error: 'Incident not found'
        };
      }

      // Generate unique token
      const token = this.generateToken();

      // Calculate expiration
      const expiresAt = new Date();
      expiresAt.setHours(expiresAt.getHours() + expiresInHours);

      // Create access token
      const accessToken = new AccessToken({
        token,
        incidentId,
        grantedBy,
        grantedTo: {
          email: grantedToEmail,
          organization,
          role
        },
        permissions: permissions || {
          canViewIncident: true,
          canViewSensorData: true,
          canViewMedia: true,
          canDownloadReport: true,
          canDownloadMedia: false
        },
        expiresAt,
        metadata: {
          caseNumber,
          requestReason
        }
      });

      await accessToken.save();

      console.log(`Access granted to ${grantedToEmail} for incident ${incidentId}`);

      return {
        success: true,
        data: {
          token,
          expiresAt,
          permissions: accessToken.permissions
        }
      };
    } catch (error) {
      console.error('Error granting access:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Verify access token
   */
  async verifyToken(token) {
    try {
      const accessToken = await AccessToken.findOne({ token })
        .populate('incidentId')
        .populate('grantedBy', 'name email');

      if (!accessToken) {
        return {
          success: false,
          error: 'Invalid token'
        };
      }

      if (!accessToken.isValid()) {
        return {
          success: false,
          error: accessToken.isRevoked ? 'Token has been revoked' : 'Token has expired'
        };
      }

      return {
        success: true,
        data: accessToken
      };
    } catch (error) {
      console.error('Error verifying token:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get incident data with access token
   */
  async getIncidentWithToken(token, ipAddress, userAgent) {
    try {
      const verification = await this.verifyToken(token);
      
      if (!verification.success) {
        return verification;
      }

      const accessToken = verification.data;

      // Log access
      await accessToken.logAccess('VIEW', ipAddress, userAgent);

      // Prepare incident data based on permissions
      const incidentData = {
        id: accessToken.incidentId._id,
        timestamp: accessToken.incidentId.timestamp,
        severity: accessToken.incidentId.severity,
        latitude: accessToken.incidentId.latitude,
        longitude: accessToken.incidentId.longitude,
        address: accessToken.incidentId.address
      };

      if (accessToken.permissions.canViewSensorData) {
        incidentData.impactForce = accessToken.incidentId.impactForce;
        incidentData.deltaV = accessToken.incidentId.deltaV;
        incidentData.confidenceScore = accessToken.incidentId.confidenceScore;
        incidentData.severityScore = accessToken.incidentId.severityScore;
        incidentData.sensorLogs = accessToken.incidentId.sensorLogs;
      }

      if (accessToken.permissions.canViewMedia) {
        incidentData.videoPath = accessToken.incidentId.videoPath;
        incidentData.thumbnailPath = accessToken.incidentId.thumbnailPath;
      }

      incidentData.vehicleData = accessToken.incidentId.vehicleData;
      incidentData.integrityStatus = accessToken.incidentId.integrityStatus;
      incidentData.integrityScore = accessToken.incidentId.integrityScore;

      return {
        success: true,
        data: {
          incident: incidentData,
          accessInfo: {
            grantedTo: accessToken.grantedTo,
            expiresAt: accessToken.expiresAt,
            permissions: accessToken.permissions
          }
        }
      };
    } catch (error) {
      console.error('Error getting incident with token:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Revoke access token
   */
  async revokeAccess(tokenId, reason, revokedBy) {
    try {
      const accessToken = await AccessToken.findById(tokenId);
      
      if (!accessToken) {
        return {
          success: false,
          error: 'Token not found'
        };
      }

      await accessToken.revoke(reason);

      console.log(`Access token ${tokenId} revoked by ${revokedBy}: ${reason}`);

      return {
        success: true,
        message: 'Access revoked successfully'
      };
    } catch (error) {
      console.error('Error revoking access:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get access tokens for an incident
   */
  async getIncidentAccessTokens(incidentId) {
    try {
      const tokens = await AccessToken.find({ incidentId })
        .populate('grantedBy', 'name email')
        .sort({ createdAt: -1 });

      return {
        success: true,
        data: tokens.map(token => ({
          id: token._id,
          grantedTo: token.grantedTo,
          grantedBy: {
            name: token.grantedBy.name,
            email: token.grantedBy.email
          },
          permissions: token.permissions,
          expiresAt: token.expiresAt,
          isRevoked: token.isRevoked,
          accessCount: token.accessLog.length,
          createdAt: token.createdAt
        }))
      };
    } catch (error) {
      console.error('Error getting access tokens:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get access audit log
   */
  async getAccessAuditLog(incidentId) {
    try {
      const tokens = await AccessToken.find({ incidentId })
        .populate('grantedBy', 'name email')
        .select('grantedTo accessLog createdAt');

      const auditLog = [];
      
      tokens.forEach(token => {
        token.accessLog.forEach(log => {
          auditLog.push({
            grantedTo: token.grantedTo,
            action: log.action,
            accessedAt: log.accessedAt,
            ipAddress: log.ipAddress,
            userAgent: log.userAgent
          });
        });
      });

      // Sort by access time (most recent first)
      auditLog.sort((a, b) => b.accessedAt - a.accessedAt);

      return {
        success: true,
        data: auditLog
      };
    } catch (error) {
      console.error('Error getting audit log:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }
}

module.exports = new AccessControlService();
