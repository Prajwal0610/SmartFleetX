const User = require('../models/User');

// @desc    Register user
// @route   POST /api/auth/register
// @access  Public
exports.register = async (req, res) => {
  try {
    const { name, email, password, phone } = req.body;

    // Check if user exists
    const userExists = await User.findOne({ email });
    if (userExists) {
      return res.status(400).json({ success: false, error: 'User already exists' });
    }

    // Create user (Note: Password hashing should be added in production)
    const user = await User.create({
      name,
      email,
      password, // In a real app, hash this!
      phone: phone || '' 
    });

    res.status(201).json({
      success: true,
      data: {
        _id: user._id,
        name: user.name,
        email: user.email
      }
    });
  } catch (err) {
    res.status(400).json({ success: false, error: err.message });
  }
};

// @desc    Login user
// @route   POST /api/auth/login
// @access  Public
exports.login = async (req, res) => {
  try {
    const { email, password } = req.body;

    // Validate email & password
    if (!email || !password) {
      return res.status(400).json({ success: false, error: 'Please provide email and password' });
    }

    // Check for user
    const user = await User.findOne({ email });
    if (!user) {
      return res.status(401).json({ success: false, error: 'Invalid credentials' });
    }

    // Check password (plain text for now as requested/implied simpler setup, add bcrypt later if needed)
    if (user.password !== password) {
         return res.status(401).json({ success: false, error: 'Invalid credentials' });
    }

    res.status(200).json({
      success: true,
      data: {
        _id: user._id,
        name: user.name,
        email: user.email,
        phone: user.phone || '',
        vehicleNumber: user.vehicleNumber || '',
        rcNumber: user.rcNumber || '',
        engineNumber: user.engineNumber || '',
        licenseNumber: user.licenseNumber || ''
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, error: 'Server Error' });
  }
};

// @desc    Update user profile
// @route   PUT /api/auth/update
// @access  Private (Public for now for simplicity)
exports.updateProfile = async (req, res) => {
  try {
    const { email, name, phone, vehicleNumber, rcNumber, engineNumber, licenseNumber } = req.body;

    // Find user by email
    let user = await User.findOne({ email });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    // Update fields
    if (name) user.name = name;
    if (phone) user.phone = phone;
    if (vehicleNumber !== undefined) user.vehicleNumber = vehicleNumber;
    if (rcNumber !== undefined) user.rcNumber = rcNumber;
    if (engineNumber !== undefined) user.engineNumber = engineNumber;
    if (licenseNumber !== undefined) user.licenseNumber = licenseNumber;

    await user.save();

    res.status(200).json({
      success: true,
      data: {
        _id: user._id,
        name: user.name,
        email: user.email,
        phone: user.phone,
        vehicleNumber: user.vehicleNumber,
        rcNumber: user.rcNumber,
        engineNumber: user.engineNumber,
        licenseNumber: user.licenseNumber
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, error: 'Server Error' });
  }
};
// @desc    Change user password
// @route   PUT /api/auth/change-password
// @access  Private
exports.changePassword = async (req, res) => {
  try {
    const { email, currentPassword, newPassword } = req.body;

    // Find user by email
    const user = await User.findOne({ email });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    // Check current password
    if (user.password !== currentPassword) {
      return res.status(401).json({ success: false, error: 'Incorrect current password' });
    }

    // Update password
    user.password = newPassword;
    await user.save();

    res.status(200).json({ success: true, message: 'Password updated successfully' });
  } catch (err) {
    res.status(500).json({ success: false, error: 'Server Error' });
  }
};
