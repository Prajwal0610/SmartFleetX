const SystemHealth = require('../models/SystemHealth');

// @desc    Get backend system status
// @route   GET /api/health/status
// @access  Public
exports.getSystemHealth = (req, res) => {
    res.status(200).json({
        success: true,
        status: 'ONLINE',
        uptime: process.uptime(),
        timestamp: Date.now()
    });
};

// @desc    Record device heartbeat
// @route   POST /api/health/heartbeat
// @access  Public (should be Protected)
exports.recordHeartbeat = async (req, res) => {
    try {
        const healthData = req.body;
        
        // Add timestamp if missing
        if (!healthData.timestamp) {
            healthData.timestamp = Date.now();
        }

        const health = await SystemHealth.create(healthData);

        res.status(201).json({
            success: true,
            data: health
        });
    } catch (err) {
        console.error('Heartbeat Error:', err);
        res.status(500).json({
            success: false,
            error: err.message
        });
    }
};

// @desc    Get device health history
// @route   GET /api/health/device/:deviceId
// @access  Public
exports.getDeviceHealth = async (req, res) => {
    try {
        const history = await SystemHealth.find({ deviceId: req.params.deviceId })
            .sort({ timestamp: -1 })
            .limit(50); // Last 50 records

        res.status(200).json({
            success: true,
            count: history.length,
            data: history
        });
    } catch (err) {
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};
