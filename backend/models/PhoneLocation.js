const mongoose = require('mongoose');

const PhoneLocationSchema = new mongoose.Schema({
    lat: {
        type: Number,
        required: true,
    },
    lang: {
        type: Number,
        required: true,
    },
    timestamp: {
        type: Date,
        default: Date.now,
    }
});

module.exports = mongoose.model('PhoneLocation', PhoneLocationSchema);
