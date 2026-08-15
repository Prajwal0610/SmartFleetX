const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
  name: {
    type: String,
    required: [true, 'Please add a name']
  },
  email: {
    type: String,
    required: [true, 'Please add an email'],
    unique: true,
    match: [
      /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/,
      'Please add a valid email'
    ]
  },
  phone: {
    type: String,
    required: false // Optional as requested
  },
  password: {
    type: String,
    required: [true, 'Please add a password'],
    minlength: 6
  },
  vehicleNumber: { type: String, default: '' },
  rcNumber: { type: String, default: '' },
  engineNumber: { type: String, default: '' },
  licenseNumber: { type: String, default: '' },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('User', UserSchema);
