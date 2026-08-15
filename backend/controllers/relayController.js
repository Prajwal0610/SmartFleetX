const RelayState = require('../models/RelayState');

// Helper: validate that value is exactly 0 or 1
const isValidRelayValue = (val) => val === '0' || val === '1' || val === 0 || val === 1;

// ─── Relay 1 ───────────────────────────────────────────────────────────────
// @desc    Set Relay 1 state
// @route   GET  /api/relay/1?state=0|1
// @route   POST /api/relay/1   body: { state: 0|1 }
// @access  Public
const setRelay1 = async (req, res) => {
    try {
        let { state } = req.method === 'GET' ? req.query : req.body;

        if (state === undefined || !isValidRelayValue(state)) {
            return res.status(400).json({
                success: false,
                message: 'Parameter "state" is required and must be 0 or 1',
            });
        }

        state = Number(state);

        state = Number(state);

        // Fetch latest state to carry over other relays
        const latest = await RelayState.findOne().sort({ timestamp: -1 });

        const newRecordData = {
            relay1: state,
            relay2: latest ? latest.relay2 : 0,
            relay3: latest ? latest.relay3 : 0,
            relay4: latest ? latest.relay4 : 0,
        };

        const record = await RelayState.create(newRecordData);

        res.status(200).json({
            success: true,
            message: `Relay 1 set to ${state === 1 ? 'ON' : 'OFF'}`,
            relay: 1,
            state,
            data: record,
        });
    } catch (error) {
        console.error('Error setting relay 1:', error.message);
        res.status(500).json({ success: false, message: 'Server error setting relay 1' });
    }
};

// ─── Relay 2 ───────────────────────────────────────────────────────────────
// @desc    Set Relay 2 state
// @route   GET  /api/relay/2?state=0|1
// @route   POST /api/relay/2   body: { state: 0|1 }
// @access  Public
const setRelay2 = async (req, res) => {
    try {
        let { state } = req.method === 'GET' ? req.query : req.body;

        if (state === undefined || !isValidRelayValue(state)) {
            return res.status(400).json({
                success: false,
                message: 'Parameter "state" is required and must be 0 or 1',
            });
        }

        state = Number(state);

        state = Number(state);

        // Fetch latest state to carry over other relays
        const latest = await RelayState.findOne().sort({ timestamp: -1 });

        const newRecordData = {
            relay1: latest ? latest.relay1 : 0,
            relay2: state,
            relay3: latest ? latest.relay3 : 0,
            relay4: latest ? latest.relay4 : 0,
        };

        const record = await RelayState.create(newRecordData);

        res.status(200).json({
            success: true,
            message: `Relay 2 set to ${state === 1 ? 'ON' : 'OFF'}`,
            relay: 2,
            state,
            data: record,
        });
    } catch (error) {
        console.error('Error setting relay 2:', error.message);
        res.status(500).json({ success: false, message: 'Server error setting relay 2' });
    }
};

// ─── Relay 3 ───────────────────────────────────────────────────────────────
// @desc    Set Relay 3 state
// @route   GET  /api/relay/3?state=0|1
// @route   POST /api/relay/3   body: { state: 0|1 }
// @access  Public
const setRelay3 = async (req, res) => {
    try {
        let { state } = req.method === 'GET' ? req.query : req.body;

        if (state === undefined || !isValidRelayValue(state)) {
            return res.status(400).json({
                success: false,
                message: 'Parameter "state" is required and must be 0 or 1',
            });
        }

        state = Number(state);

        state = Number(state);

        // Fetch latest state to carry over other relays
        const latest = await RelayState.findOne().sort({ timestamp: -1 });

        const newRecordData = {
            relay1: latest ? latest.relay1 : 0,
            relay2: latest ? latest.relay2 : 0,
            relay3: state,
            relay4: latest ? latest.relay4 : 0,
        };

        const record = await RelayState.create(newRecordData);

        res.status(200).json({
            success: true,
            message: `Relay 3 set to ${state === 1 ? 'ON' : 'OFF'}`,
            relay: 3,
            state,
            data: record,
        });
    } catch (error) {
        console.error('Error setting relay 3:', error.message);
        res.status(500).json({ success: false, message: 'Server error setting relay 3' });
    }
};

// ─── Relay 4 ───────────────────────────────────────────────────────────────
// @desc    Set Relay 4 state
// @route   GET  /api/relay/4?state=0|1
// @route   POST /api/relay/4   body: { state: 0|1 }
// @access  Public
const setRelay4 = async (req, res) => {
    try {
        let { state } = req.method === 'GET' ? req.query : req.body;

        if (state === undefined || !isValidRelayValue(state)) {
            return res.status(400).json({
                success: false,
                message: 'Parameter "state" is required and must be 0 or 1',
            });
        }

        state = Number(state);

        state = Number(state);

        // Fetch latest state to carry over other relays
        const latest = await RelayState.findOne().sort({ timestamp: -1 });

        const newRecordData = {
            relay1: latest ? latest.relay1 : 0,
            relay2: latest ? latest.relay2 : 0,
            relay3: latest ? latest.relay3 : 0,
            relay4: state,
        };

        const record = await RelayState.create(newRecordData);

        res.status(200).json({
            success: true,
            message: `Relay 4 set to ${state === 1 ? 'ON' : 'OFF'}`,
            relay: 4,
            state,
            data: record,
        });
    } catch (error) {
        console.error('Error setting relay 4:', error.message);
        res.status(500).json({ success: false, message: 'Server error setting relay 4' });
    }
};

// ─── Get latest state of all relays ───────────────────────────────────────
// @desc    Get latest relay states
// @route   GET /api/relay/status
// @access  Public
const getRelayStatus = async (req, res) => {
    try {
        const latest = await RelayState.findOne().sort({ timestamp: -1 });

        if (!latest) {
            return res.status(200).json({
                success: true,
                message: 'No relay state found, all relays defaulting to OFF',
                relay1: 0,
                relay2: 0,
                relay3: 0,
                relay4: 0,
            });
        }

        res.status(200).json({
            success: true,
            relay1: latest.relay1,
            relay2: latest.relay2,
            relay3: latest.relay3,
            relay4: latest.relay4,
            timestamp: latest.timestamp,
        });
    } catch (error) {
        console.error('Error fetching relay status:', error.message);
        res.status(500).json({ success: false, message: 'Server error fetching relay status' });
    }
};

module.exports = { setRelay1, setRelay2, setRelay3, setRelay4, getRelayStatus };
