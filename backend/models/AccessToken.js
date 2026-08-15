const mongoose = require('mongoose');

/**
 * Access Token Schema
 * Time-limited tokens for authority/insurance access to incident data
 */
const AccessTokenSchema = new mongoose.Schema({
  token: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  
  incidentId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Incident',
    required: true
  },
  
  grantedTo: {
    email: { type: String, required: true },
    organization: String,
    role: {
      type: String,
      enum: ['AUTHORITY', 'INSURANCE', 'LEGAL'],
      required: true
    }
  },
  
  grantedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  
  permissions: {
    canViewIncident: { type: Boolean, default: true },
    canViewSensorData: { type: Boolean, default: true },
    canViewMedia: { type: Boolean, default: true },
    canDownloadReport: { type: Boolean, default: true  },
    canDownloadMedia: { type: Boolean, default: false }
  },
  
  expiresAt: {
    type: Date,
    required: true,
    index: true
  },
  
  isRevoked: {
    type: Boolean,
    default: false
  },
  
  revokedAt: Date,
  revokedReason: String,
  
  accessLog: [{
    accessedAt: { type: Date, default: Date.now },
    ipAddress: String,
    action: String, // 'VIEW', 'DOWNLOAD_REPORT', 'DOWNLOAD_MEDIA'
    userAgent: String
  }],
  
  metadata: {
    caseNumber: String,
    requestReason: String,
    notes: String
  }
}, {
  timestamps: true
});

// Index for token expiration cleanup
AccessTokenSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });

// Methods
AccessTokenSchema.methods.isValid = function() {
  return !this.isRevoked && new Date() < this.expiresAt;
};

AccessTokenSchema.methods.logAccess = function(action, ipAddress, userAgent) {
  this.accessLog.push({
    accessedAt: new Date(),
    ipAddress,
    action,
    userAgent
  });
  return this.save();
};

AccessTokenSchema.methods.revoke = function(reason) {
  this.isRevoked = true;
  this.revokedAt = new Date();
  this.revokedReason = reason;
  return this.save();
};

module.exports = mongoose.model('AccessToken', AccessTokenSchema);
