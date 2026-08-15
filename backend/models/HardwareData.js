const mongoose = require('mongoose');

const hardwareDataSchema = new mongoose.Schema({
    lat: {
        type: Number,
        required: true,
    },
    lang: {
        type: Number,
        required: true,
    },
    speed: {
        type: Number,
        required: true,
    },
    force: {
        type: Number,
        required: true,
    },
    pwm: {
        type: Number,
        required: true,
    },
    timestamp: {
        type: Date,
        default: Date.now,
    },
});

module.exports = mongoose.model('HardwareData', hardwareDataSchema);
