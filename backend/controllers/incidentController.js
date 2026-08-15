const Incident = require('../models/Incident');

// @desc    Get all incidents
// @route   GET /api/incidents
// @access  Public
exports.getIncidents = async (req, res) => {
  try {
    const incidents = await Incident.find().sort({ timestamp: -1 });
    res.status(200).json({ success: true, count: incidents.length, data: incidents });
  } catch (err) {
    res.status(500).json({ success: false, error: 'Server Error' });
  }
};

// @desc    Get single incident
// @route   GET /api/incidents/:id
// @access  Public
exports.getIncident = async (req, res) => {
  try {
    const incident = await Incident.findOne({ androidId: req.params.id });
    if (!incident) {
      return res.status(404).json({ success: false, error: 'Incident not found' });
    }
    res.status(200).json({ success: true, data: incident });
  } catch (err) {
    res.status(500).json({ success: false, error: 'Server Error' });
  }
};

// @desc    Create new incident
// @route   POST /api/incidents
// @access  Public
exports.createIncident = async (req, res) => {
  try {
    const incident = await Incident.create(req.body);
    res.status(201).json({ success: true, data: incident });
  } catch (err) {
    if (err.code === 11000) {
        return res.status(400).json({ success: false, error: 'Incident already exists' });
    }
    res.status(400).json({ success: false, error: err.message });
  }
};

exports.updateAnalysis = async (req, res) => {
    try {
        const incident = await Incident.findOneAndUpdate(
            { androidId: req.params.id }, 
            { 
                faultDetermination: req.body.faultDetermination,
                analysisNotes: req.body.analysisNotes,
                analyzedAt: Date.now()
            },
            { new: true, runValidators: true }
        );
        if (!incident) {
            return res.status(404).json({ success: false, error: 'Incident not found' });
        }
        res.status(200).json({ success: true, data: incident });
    } catch (err) {
        res.status(500).json({ success: false, error: 'Server Error' });
    }
}

// @desc    Sync batch of incidents
// @route   POST /api/incidents/sync
// @access  Public
exports.syncIncidents = async (req, res) => {
  try {
    const incidents = req.body; 
    
    // Handle both single object and array
    const batch = Array.isArray(incidents) ? incidents : [incidents];
    const results = [];
    
    for (const incidentData of batch) {
        // Upsert based on androidId (Last Write Wins)
        const result = await Incident.findOneAndUpdate(
            { androidId: incidentData.androidId },
            incidentData,
            { new: true, upsert: true, setDefaultsOnInsert: true }
        );
        results.push(result.androidId);
    }

    res.status(200).json({ 
        success: true, 
        syncedCount: results.length,
        syncedIds: results 
    });
  } catch (err) {
    console.error('Sync Error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};
