const http = require('http');

// Test data
const lat = 18.5204;
const lang = 73.8567;
const speed = 45;
const force = 1.2;
const pwm = 100;

const url = `http://localhost:5000/api/hardware?lat=${lat}&lang=${lang}&speed=${speed}&force=${force}&pwm=${pwm}`;

console.log(`Testing hardware API with PWM...`);
console.log(`URL: ${url}`);

http.get(url, (res) => {
    console.log(`STATUS: ${res.statusCode}`);
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
        console.log('BODY: ' + data);

        // Now check the latest data
        console.log('\nFetching latest hardware data...');
        http.get('http://localhost:5000/api/hardware/latest', (resLatest) => {
            let latestData = '';
            resLatest.on('data', (chunk) => { latestData += chunk; });
            resLatest.on('end', () => {
                console.log('LATEST DATA: ' + latestData);
            });
        });
    });
}).on('error', (e) => {
    console.error(`Error: ${e.message}`);
});
