const express = require('express');
const router = express.Router();
const { 
    getSystemHealth, 
    recordHeartbeat, 
    getDeviceHealth 
} = require('../controllers/healthController');

router.get('/status', getSystemHealth);
router.post('/heartbeat', recordHeartbeat);
router.get('/device/:deviceId', getDeviceHealth);

module.exports = router;
