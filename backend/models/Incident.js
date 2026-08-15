const mongoose = require('mongoose');

const VehicleDataSchema = new mongoose.Schema({
  speed: Number,
  rpm: Number,
  fuelLevel: Number,
  coolantTemp: Number,
  engineLoad: Number,
  hardBrake: Boolean,
  rapidAcceleration: Boolean,
  gForce: Number,
  dtcCodes: [String],
  timestamp: Number
});

const DriverStateSchema = new mongoose.Schema({
  drowsy: Boolean,
  distracted: Boolean,
  attentionScore: Number,
  headPose: { 
      type: String, 
      enum: ["FORWARD", "LEFT", "RIGHT", "DOWN", "UP"],
      default: "FORWARD"
  },
  eyesClosed: Boolean,
  yawning: Boolean,
  phoneUsage: Boolean,
  blinksPerMinute: Number,
  timestamp: Number
});

const IncidentSchema = new mongoose.Schema({
  // Use a custom ID or let MongoDB generate _id. keeping android ID as a field for sync
  androidId: { type: String, unique: true }, 
  timestamp: { type: Number, required: true },
  type: { 
    type: String, 
    enum: ['ACCIDENT', 'HARD_BRAKE', 'RAPID_ACCELERATION', 'MANUAL'],
    default: 'MANUAL'
  },
  severity: {
    type: String,
    enum: ['MINOR', 'MODERATE', 'SEVERE'],
    default: 'MINOR'
  },
  
  // Severity metrics (new)
  severityScore: { type: Number, min: 0, max: 100 },
  severityConfidence: { type: Number, min: 0, max: 1 },
  impactForce: Number,  // G-force magnitude
  deltaV: Number,       // Speed change in m/s
  confidenceScore: { type: Number, min: 0, max: 100 },
  
  // Location
  latitude: Number,
  longitude: Number,
  address: String,
  bearing: Number,
  
  // Media
  videoPath: String,
  driverVideoPath: String,
  thumbnailPath: String,
  
  // Nested Data
  vehicleData: VehicleDataSchema,
  driverState: DriverStateSchema,
  
  // Sensor logs (new)
  sensorLogs: [{
    timestamp: Number,
    gForceX: Number,
    gForceY: Number,
    gForceZ: Number,
    gForceMagnitude: Number,
    gyroX: Number,
    gyroY: Number,
    gyroZ: Number,
    speed: Number,
    latitude: Number,
    longitude: Number
  }],
  
  // Analysis
  faultDetermination: String,
  analysisNotes: String,
  reportGenerated: Boolean,
  reportPath: String,
  
  // Evidence integrity (new)
  dataHash: String,           // SHA-256 hash
  integrityScore: { type: Number, min: 0, max: 1 },
  verifiedAt: Number,
  integrityStatus: {
    type: String,
    enum: ['VERIFIED', 'UNVERIFIED', 'TAMPERED'],
    default: 'UNVERIFIED'
  },
  
  // Insurance
  insuranceClaimFiled: Boolean,
  claimNumber: String,
  
  // Timestamps
  createdAt: { type: Number, default: Date.now },
  analyzedAt: Number
}, {
  timestamps: true // adds createdAt and updatedAt (Mongoose standard)
});

module.exports = mongoose.model('Incident', IncidentSchema);
