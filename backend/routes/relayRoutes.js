const express = require('express');
const router = express.Router();
const {
    setRelay1,
    setRelay2,
    setRelay3,
    setRelay4,
    getRelayStatus,
} = require('../controllers/relayController');

// GET/POST /api/relay/1?state=0 or state=1
router.route('/1').get(setRelay1).post(setRelay1);

// GET/POST /api/relay/2?state=0 or state=1
router.route('/2').get(setRelay2).post(setRelay2);

// GET/POST /api/relay/3?state=0 or state=1
router.route('/3').get(setRelay3).post(setRelay3);

// GET/POST /api/relay/4?state=0 or state=1
router.route('/4').get(setRelay4).post(setRelay4);

// GET /api/relay/status  — fetch latest state of all 4 relays
router.get('/status', getRelayStatus);

module.exports = router;
