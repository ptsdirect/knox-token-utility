import fs from 'fs';
import jwt from 'jsonwebtoken';

try {
    // Read private key
    const privateKey = fs.readFileSync('./private_key.pem', 'utf8');

    // Define JWT payload
    const payload = {
        sub: process.env.KNOX_APP_ID || 'your-client-identifier',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 60 * 30, // 30 minutes expiry
    };

    // Sign JWT
    const token = jwt.sign(payload, privateKey, { algorithm: 'RS256' });

    // Print token with clear formatting
    console.log('\n=== YOUR JWT TOKEN ===\n');
    console.log(token);
    console.log('\n====================\n');

    // Also write to file
    fs.writeFileSync('jwt.txt', token);
} catch (error) {
    console.error('Error generating JWT:', error.message);
}
