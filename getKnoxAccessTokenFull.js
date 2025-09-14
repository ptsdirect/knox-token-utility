import fs from 'fs';
import jwt from 'jsonwebtoken';
import axios from 'axios';

async function getKnoxAccessToken() {
    try {
        // Read private key for JWT signing
        const privateKey = fs.readFileSync('./private_key.pem', 'utf8');
        
        // Get base64EncodedStringPublicKey
        const publicKey = fs.readFileSync('./public_key.pem', 'utf8');
        const base64EncodedStringPublicKey = publicKey
            .replace('-----BEGIN PUBLIC KEY-----', '')
            .replace('-----END PUBLIC KEY-----', '')
            .replace(/[\n\r]/g, '')
            .trim();

        // Create JWT payload
        const payload = {
            sub: process.env.KNOX_APP_ID || 'your-client-identifier',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 60 * 30
        };

        // Generate clientIdentifierJwt
        const clientIdentifierJwt = jwt.sign(payload, privateKey, { algorithm: 'RS256' });

        console.log('\n=== REQUEST PARAMETERS ===\n');
        console.log('base64EncodedStringPublicKey:', base64EncodedStringPublicKey);
        console.log('\nclientIdentifierJwt:', clientIdentifierJwt);
        
        // Make Knox API request
        const response = await axios.post('https://kcs.samsungknox.com/kcs/v1/ses/token', {
            base64EncodedStringPublicKey: base64EncodedStringPublicKey,
            clientIdentifierJwt: clientIdentifierJwt,
            validityForAccessTokenInMinutes: 30
        });

        console.log('\n=== API RESPONSE ===\n');
        console.log(JSON.stringify(response.data, null, 2));

        return response.data;

    } catch (error) {
        console.error('\nError:', error.response?.data || error.message);
        throw error;
    }
}

// Run the function
getKnoxAccessToken();