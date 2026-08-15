const mongoose = require('mongoose');

const SystemHealthSchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    index: true
  },
  timestamp: {
    type: Date,
    default: Date.now
  },
  overallScore: Number,
  batteryLevel: Number,
  batteryScore: Number,
  storageFree: Number,
  storageScore: Number,
  networkType: String,
  networkScore: Number,
  sensorsAvailable: Number,
  sensorScore: Number,
  status: {
    type: String,
    enum: ['HEALTHY', 'WARNING', 'CRITICAL'],
    default: 'HEALTHY'
  }
});

module.exports = mongoose.model('SystemHealth', SystemHealthSchema);
