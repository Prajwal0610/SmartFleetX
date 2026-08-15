const express = require('express');
const router = express.Router();
const { receiveData, getLatestData, updatePhoneLocation } = require('../controllers/hardwareController');

// Route for getting latest hardware telemetry
router.get('/latest', getLatestData);

// Route for incoming hardware telemetry
// Matches both GET and POST using `.all`, or explicit `.get` and `.post`
router.route('/')
    .get(receiveData)
    .post(receiveData);

// Route for updating smartphone location
router.post('/phone-location', updatePhoneLocation);

module.exports = router;
