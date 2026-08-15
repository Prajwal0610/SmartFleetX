const mongoose = require('mongoose');

const relayStateSchema = new mongoose.Schema({
    relay1: {
        type: Number,
        enum: [0, 1],
        default: 0,
    },
    relay2: {
        type: Number,
        enum: [0, 1],
        default: 0,
    },
    relay3: {
        type: Number,
        enum: [0, 1],
        default: 0,
    },
    relay4: {
        type: Number,
        enum: [0, 1],
        default: 0,
    },
    timestamp: {
        type: Date,
        default: Date.now,
    },
});

module.exports = mongoose.model('RelayState', relayStateSchema);
