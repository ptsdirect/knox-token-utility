import fs from 'fs';

try {
    // Read public key
    const publicKey = fs.readFileSync('./public_key.pem', 'utf8');
    
    // Remove headers, footers, and newlines
    const cleanKey = publicKey
        .replace('-----BEGIN PUBLIC KEY-----', '')
        .replace('-----END PUBLIC KEY-----', '')
        .replace(/[\n\r]/g, '')
        .trim();
    
    console.log('\n=== YOUR BASE64 ENCODED PUBLIC KEY ===\n');
    console.log(cleanKey);
    console.log('\n=====================================\n');

    // Save to file
    fs.writeFileSync('public_key_base64.txt', cleanKey);
} catch (error) {
    console.error('Error encoding public key:', error.message);
}