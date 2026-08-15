const express = require('express');
const router = express.Router();
const { 
  getIncidents, 
  getIncident, 
  createIncident, 
  updateAnalysis,
  syncIncidents 
} = require('../controllers/incidentController');

router.route('/')
  .get(getIncidents)
  .post(createIncident);

router.route('/sync')
  .post(syncIncidents);

router.route('/:id')
  .get(getIncident);

router.route('/:id/analysis')
  .put(updateAnalysis);

module.exports = router;
