const HardwareData = require('../models/HardwareData');
const PhoneLocation = require('../models/PhoneLocation');

// In-memory cache for latest smartphone location
let latestPhoneLocation = null;

// @desc    Receive hardware data (lat, lang, speed, force)
// @route   GET /api/hardware
// @route   POST /api/hardware
// @access  Public
const receiveData = async (req, res) => {
    try {
        // Extract data from query params (GET) or body (POST)
        let { lat, lang, speed, force, pwm } = req.method === 'GET' ? req.query : req.body;

        // Integrate smartphone location if available and if lat/lang are missing or default
        if (!latestPhoneLocation) {
            try {
                const latest = await PhoneLocation.findOne().sort({ timestamp: -1 });
                if (latest) {
                    latestPhoneLocation = {
                        lat: latest.lat,
                        lang: latest.lang,
                        timestamp: latest.timestamp
                    };
                }
            } catch (err) {
                console.error('Error loading latest phone location from DB:', err.message);
            }
        }

        if (latestPhoneLocation) {
            if (!lat || !lang || Number(lat) === 0 || Number(lang) === 0 || Number(lat) === 18.5204) {
                lat = latestPhoneLocation.lat;
                lang = latestPhoneLocation.lang;
            }
        }

        // Fallback to defaults if lat/lang are still missing or zero
        if (lat === undefined || lat === null || Number(lat) === 0) lat = 18.5204;
        if (lang === undefined || lang === null || Number(lang) === 0) lang = 73.8567;

        // Validate required fields
        if (speed === undefined || force === undefined || pwm === undefined) {
            return res.status(400).json({
                success: false,
                message: 'Please provide speed, force, and pwm parameters',
            });
        }

        // Convert to numbers and format to 6 decimal places (where applicable)
        lat = Number(Number(lat).toFixed(6));
        lang = Number(Number(lang).toFixed(6));
        speed = Number(Number(speed).toFixed(6));
        force = Number(Number(force).toFixed(6));
        pwm = Number(pwm);

        if (isNaN(lat) || isNaN(lang) || isNaN(speed) || isNaN(force) || isNaN(pwm)) {
            return res.status(400).json({
                success: false,
                message: 'Parameters must be valid numbers',
            });
        }

        // Create and save new hardware data record
        const newHardwareData = await HardwareData.create({
            lat,
            lang,
            speed,
            force,
            pwm,
        });

        res.status(201).json({
            success: true,
            message: 'Hardware data saved successfully',
            data: newHardwareData,
        });
    } catch (error) {
        console.error('Error saving hardware data:', error.message);
        res.status(500).json({
            success: false,
            message: 'Server Error saving hardware data',
        });
    }
};

// @desc    Get latest hardware data
// @route   GET /api/hardware/latest
// @access  Public
const getLatestData = async (req, res) => {
    try {
        const latest = await HardwareData.findOne().sort({ timestamp: -1 });

        if (!latest) {
            return res.status(200).json({
                success: true,
                message: 'No hardware data available',
                data: null
            });
        }

        // Convert mongoose document to plain JS object to allow modification
        const latestObj = latest.toObject();

        // Integrate smartphone location if latest hardware coordinates are missing or zero
        if (!latestObj.lat || !latestObj.lang || Number(latestObj.lat) === 0 || Number(latestObj.lang) === 0 || Number(latestObj.lat) === 18.5204) {
            if (!latestPhoneLocation) {
                try {
                    const latestPhone = await PhoneLocation.findOne().sort({ timestamp: -1 });
                    if (latestPhone) {
                        latestPhoneLocation = {
                            lat: latestPhone.lat,
                            lang: latestPhone.lang,
                            timestamp: latestPhone.timestamp
                        };
                    }
                } catch (err) {
                    console.error('Error loading latest phone location from DB:', err.message);
                }
            }

            if (latestPhoneLocation) {
                latestObj.lat = latestPhoneLocation.lat;
                latestObj.lang = latestPhoneLocation.lang;
            } else {
                // Fallback to Pune defaults if absolutely no location is found
                if (!latestObj.lat || Number(latestObj.lat) === 0) latestObj.lat = 18.5204;
                if (!latestObj.lang || Number(latestObj.lang) === 0) latestObj.lang = 73.8567;
            }
        }

        res.status(200).json({
            success: true,
            data: latestObj
        });
    } catch (error) {
        console.error('Error fetching latest hardware data:', error.message);
        res.status(500).json({
            success: false,
            message: 'Server Error fetching latest hardware data'
        });
    }
};

// @desc    Update smartphone location
// @route   POST /api/hardware/phone-location
// @access  Public
const updatePhoneLocation = async (req, res) => {
    try {
        const { lat, lang } = req.body;

        if (lat === undefined || lang === undefined) {
            return res.status(400).json({
                success: false,
                message: 'Please provide both lat and lang parameters in the request body',
            });
        }

        latestPhoneLocation = {
            lat: Number(Number(lat).toFixed(6)),
            lang: Number(Number(lang).toFixed(6)),
            timestamp: new Date()
        };

        // Persist to MongoDB
        await PhoneLocation.create({
            lat: latestPhoneLocation.lat,
            lang: latestPhoneLocation.lang
        });

        res.status(200).json({
            success: true,
            message: 'Phone location updated successfully',
            data: latestPhoneLocation
        });
    } catch (error) {
        console.error('Error updating phone location:', error.message);
        res.status(500).json({
            success: false,
            message: 'Server Error updating phone location'
        });
    }
};

module.exports = {
    receiveData,
    getLatestData,
    updatePhoneLocation,
};
