const http = require('http');

const postData = JSON.stringify({
  androidId: "TEST_INC_" + Date.now(),
  timestamp: Date.now(),
  type: "HARD_BRAKE",
  vehicleData: {
    speed: 85,
    rpm: 3000
  },
  driverState: {
    drowsy: false,
    attentionScore: 95
  }
});

const options = {
  hostname: 'localhost',
  port: 5000,
  path: '/api/incidents',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': postData.length
  }
};

console.log('Sending POST request...');
const req = http.request(options, (res) => {
  console.log(`STATUS: ${res.statusCode}`);
  res.setEncoding('utf8');
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log('BODY: ' + data);
    
    // Now Do GET
    console.log('\nSending GET request...');
    http.get('http://localhost:5000/api/incidents', (res) => {
        console.log(`STATUS: ${res.statusCode}`);
        let getData = '';
        res.on('data', (chunk) => { getData += chunk; });
        res.on('end', () => {
            console.log('BODY: ' + getData);
        });
    });
  });
});

req.on('error', (e) => {
  console.error(`problem with request: ${e.message}`);
});

req.write(postData);
req.end();
