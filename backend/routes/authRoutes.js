const express = require('express');
const { register, login } = require('../controllers/authController');

const router = express.Router();

router.post('/register', register);
router.post('/login', login);
router.put('/update', require('../controllers/authController').updateProfile);
router.put('/change-password', require('../controllers/authController').changePassword);

module.exports = router;
