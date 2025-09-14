import fs from 'fs';
import jwt from 'jsonwebtoken';

try {
    // Read private key for JWT signing
    const privateKey = fs.readFileSync('./private_key.pem', 'utf8');
    
    // Read public key and encode to base64
    const publicKey = fs.readFileSync('./public_key.pem', 'utf8');
    const base64EncodedPublicKey = publicKey
        .replace('-----BEGIN PUBLIC KEY-----', '')
        .replace('-----END PUBLIC KEY-----', '')
        .replace(/[\n\r]/g, '')
        .trim();

    // Create JWT payload
    const payload = {
        sub: process.env.KNOX_APP_ID || 'your-client-identifier',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 60 * 30 // 30 minutes expiry
    };

    // Sign JWT
    const clientIdentifierJwt = jwt.sign(payload, privateKey, { algorithm: 'RS256' });

    // Print both values with clear formatting
    console.log('\n=== YOUR BASE64 ENCODED PUBLIC KEY ===\n');
    console.log(base64EncodedPublicKey);
    console.log('\n=== YOUR CLIENT IDENTIFIER JWT ===\n');
    console.log(clientIdentifierJwt);
    console.log('\n===================================\n');

    // Save to files for easy access
    fs.writeFileSync('knox_credentials.json', JSON.stringify({
        base64EncodedPublicKey,
        clientIdentifierJwt
    }, null, 2));

} catch (error) {
    console.error('Error:', error.message);
}