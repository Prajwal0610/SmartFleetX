const express = require('express');
const dotenv = require('dotenv');
const cors = require('cors');
const connectDB = require('./config/db');

// Load env vars
dotenv.config();

// Connect to database
connectDB();

const app = express();

// Middleware
app.use(express.json());
app.use(cors());

// Routes
const incidentRoutes = require('./routes/incidentRoutes');
const authRoutes = require('./routes/authRoutes');
const analyticsRoutes = require('./routes/analyticsRoutes');
const accessRoutes = require('./routes/accessRoutes');
const hardwareRoutes = require('./routes/hardwareRoutes');
const relayRoutes = require('./routes/relayRoutes');

app.get('/', (req, res) => {
  res.send('Smart Fleet X API is running');
});

app.use('/api/incidents', incidentRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/analytics', analyticsRoutes);
app.use('/api/access', accessRoutes);
app.use('/api/health', require('./routes/healthRoutes'));
app.use('/api/hardware', hardwareRoutes);
app.use('/api/relay', relayRoutes);



const PORT = process.env.PORT || 5000;

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
