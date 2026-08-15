const http = require('http');

const registerData = JSON.stringify({
  name: "Test User",
  email: "test" + Date.now() + "@fleet.com",
  password: "password123"
});

const optionsRegister = {
  hostname: 'localhost',
  port: 5000,
  path: '/api/auth/register',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': registerData.length
  }
};

console.log('Testing Register...');
const reqReg = http.request(optionsRegister, (res) => {
  console.log(`REGISTER STATUS: ${res.statusCode}`);
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log('REGISTER BODY: ' + data);
    
    // Test Login
    const loginData = JSON.stringify({
        email: JSON.parse(data).data.email,
        password: "password123"
    });
    
    const optionsLogin = {
        hostname: 'localhost',
        port: 5000,
        path: '/api/auth/login',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': loginData.length
        }
    };
    
    console.log('\nTesting Login...');
    const reqLogin = http.request(optionsLogin, (resLogin) => {
        console.log(`LOGIN STATUS: ${resLogin.statusCode}`);
        let loginResData = '';
        resLogin.on('data', (chunk) => { loginResData += chunk; });
        resLogin.on('end', () => {
            console.log('LOGIN BODY: ' + loginResData);
        });
    });
    
    reqLogin.write(loginData);
    reqLogin.end();
  });
});

reqReg.write(registerData);
reqReg.end();
