/**
 * Samsung Knox Access Token Generator
 * 
 * This script generates Samsung Knox access tokens with proper JWT claims.
 * It supports both RS256 (RSA) and ES256 (Elliptic Curve) algorithms, auto-detecting 
 * the appropriate algorithm based on the key type.
 * 
 * It handles the complete flow from JWT generation to API request and signed access token creation.
 * 
 * Usage:
 *   node generateKnoxAccessToken.js [--keys-file <path>] [--client-id <id>] [--region <us|eu|ap>]
 * 
 * Environment Variables:
 *   KNOX_CLIENT_ID - Client identifier (default: 'your-client-identifier')
 *   KNOX_REGION - Knox API region: us, eu, or ap (default: 'us')
 *   KNOX_API_BASE_URL - Override the base URL for Knox API
 */

import fs from 'fs';
import jwt from 'jsonwebtoken';
import axios from 'axios';
import { randomUUID } from 'crypto';

/**
 * Configuration object for script parameters
 */
const config = {
  keysFile: process.argv.find(arg => arg.startsWith('--keys-file='))?.split('=')[1] || 'keys.json',
  clientId: process.argv.find(arg => arg.startsWith('--client-id='))?.split('=')[1] || process.env.KNOX_CLIENT_ID || 'your-client-identifier',
  region: process.argv.find(arg => arg.startsWith('--region='))?.split('=')[1] || process.env.KNOX_REGION || 'us',
  tokenValidityMinutes: 30
};

/**
 * Resolve Knox API base URL based on region
 * @param {string} region - Region code (us, eu, ap)
 * @returns {string} Base URL for the Knox API
 */
function getKnoxApiBaseUrl(region) {
  if (process.env.KNOX_API_BASE_URL) {
    return process.env.KNOX_API_BASE_URL;
  }
  
  const regionLower = region.toLowerCase();
  switch (regionLower) {
    case 'eu':
      return 'https://eu-api.samsungknox.com/kcs/v1';
    case 'ap':
      return 'https://ap-api.samsungknox.com/kcs/v1';
    case 'us':
    default:
      return 'https://us-api.samsungknox.com/kcs/v1';
  }
}

/**
 * Load keys from JSON file
 * @param {string} filePath - Path to keys.json file
 * @returns {Object} Object containing privateKey and publicKey
 */
function loadKeys(filePath) {
  try {
    const keysData = fs.readFileSync(filePath, 'utf8');
    const keys = JSON.parse(keysData);
    
    if (!keys.privateKey || !keys.publicKey) {
      throw new Error('keys.json must contain both privateKey and publicKey in PEM format');
    }
    
    return keys;
  } catch (error) {
    if (error.code === 'ENOENT') {
      console.error(`\nError: Keys file not found at ${filePath}`);
      console.error('Please create a keys.json file with privateKey and publicKey in PEM format.');
      console.error('See keys.json.example for reference.\n');
    } else if (error instanceof SyntaxError) {
      console.error(`\nError: Invalid JSON in ${filePath}`);
      console.error(error.message);
    } else {
      console.error('\nError loading keys:', error.message);
    }
    throw error;
  }
}

// Key size threshold for algorithm detection (in base64 characters)
// EC P-256 keys are ~150 chars, RSA 2048 keys are ~1700 chars
const KEY_SIZE_THRESHOLD = 500;

/**
 * Detect key type from private key PEM
 * @param {string} privateKey - PEM formatted private key
 * @returns {string} Algorithm to use ('ES256' for EC, 'RS256' for RSA)
 * @throws {Error} If key type cannot be determined
 */
function detectKeyAlgorithm(privateKey) {
  const keyContent = privateKey
    .replace(/-----BEGIN [A-Z ]+-----/, '')
    .replace(/-----END [A-Z ]+-----/, '')
    .replace(/[\n\r]/g, '')
    .trim();
  
  // RSA 2048 keys are ~1700 chars in base64, EC P-256 keys are ~150 chars
  if (keyContent.length < KEY_SIZE_THRESHOLD) {
    // Short key - likely EC P-256
    if (privateKey.includes('BEGIN EC PRIVATE KEY') || privateKey.includes('BEGIN PRIVATE KEY')) {
      return 'ES256';
    }
  } else {
    // Long key - likely RSA
    if (privateKey.includes('BEGIN RSA PRIVATE KEY') || privateKey.includes('BEGIN PRIVATE KEY')) {
      return 'RS256';
    }
  }
  
  // If we can't determine the key type, throw an error
  throw new Error(
    `Unable to detect key algorithm. Key size: ${keyContent.length} chars. ` +
    `Expected EC P-256 (< ${KEY_SIZE_THRESHOLD} chars) or RSA 2048 (> ${KEY_SIZE_THRESHOLD} chars). ` +
    `Please verify your key format.`
  );
}

/**
 * Encode public key to base64 format for Knox API
 * @param {string} publicKey - PEM formatted public key
 * @returns {string} Base64 encoded public key without headers/footers
 */
function encodePublicKeyToBase64(publicKey) {
  return publicKey
    .replace('-----BEGIN PUBLIC KEY-----', '')
    .replace('-----END PUBLIC KEY-----', '')
    .replace(/[\n\r]/g, '')
    .trim();
}

/**
 * Generate a client identifier JWT with all required claims
 * @param {string} privateKey - PEM formatted private key
 * @param {string} clientId - Client identifier
 * @param {string} baseUrl - Knox API base URL
 * @param {string} algorithm - Signing algorithm (ES256 or RS256)
 * @returns {string} Signed JWT token
 */
function generateClientIdentifierJwt(privateKey, clientId, baseUrl, algorithm) {
  const now = Math.floor(Date.now() / 1000);
  
  // JWT payload with all required claims
  const payload = {
    iss: clientId,                                    // Issuer: client identifier
    aud: baseUrl + '/ses/token',                      // Audience: Knox token endpoint
    sub: clientId,                                     // Subject: client identifier
    iat: now,                                          // Issued at: current timestamp
    exp: now + (config.tokenValidityMinutes * 60),    // Expiration: 30 minutes
    jti: randomUUID(),                                 // JWT ID: unique identifier
    clientIdentifier: clientId                         // Custom claim: client identifier
  };
  
  // Sign JWT with detected algorithm (ES256 for EC keys, RS256 for RSA keys)
  const token = jwt.sign(payload, privateKey, { algorithm });
  
  return token;
}

/**
 * Request access token from Knox API
 * @param {string} baseUrl - Knox API base URL
 * @param {string} base64PublicKey - Base64 encoded public key
 * @param {string} clientIdentifierJwt - Client identifier JWT
 * @returns {Promise<Object>} API response data
 */
async function requestAccessToken(baseUrl, base64PublicKey, clientIdentifierJwt) {
  const endpoint = `${baseUrl}/ses/token`;
  
  const requestData = {
    base64EncodedStringPublicKey: base64PublicKey,
    validityForAccessTokenInMinutes: config.tokenValidityMinutes
  };
  
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-SES-JWT': clientIdentifierJwt,
    'X-KNOX-API-VERSION': 'v1'
  };
  
  try {
    const response = await axios.post(endpoint, requestData, { headers });
    return response.data;
  } catch (error) {
    if (error.response) {
      throw new Error(`Knox API request failed (HTTP ${error.response.status}): ${JSON.stringify(error.response.data)}`);
    } else if (error.request) {
      throw new Error('Knox API request failed: No response received from server');
    } else {
      throw new Error(`Knox API request failed: ${error.message}`);
    }
  }
}

/**
 * Generate a signed access token JWT
 * @param {string} privateKey - PEM formatted private key
 * @param {string} accessToken - Access token from Knox API
 * @param {string} clientId - Client identifier
 * @param {string} algorithm - Signing algorithm (ES256 or RS256)
 * @returns {string} Signed access token JWT
 */
function generateSignedAccessTokenJwt(privateKey, accessToken, clientId, algorithm) {
  const now = Math.floor(Date.now() / 1000);
  
  const payload = {
    iss: clientId,
    aud: 'knox-guard',
    sub: clientId,
    iat: now,
    exp: now + (config.tokenValidityMinutes * 60),
    jti: randomUUID(),
    accessToken: accessToken
  };
  
  return jwt.sign(payload, privateKey, { algorithm });
}

/**
 * Main function to orchestrate the token generation flow
 */
async function main() {
  console.log('\n=== Samsung Knox Access Token Generator ===\n');
  console.log(`Keys File: ${config.keysFile}`);
  console.log(`Client ID: ${config.clientId}`);
  console.log(`Region: ${config.region}`);
  console.log(`Token Validity: ${config.tokenValidityMinutes} minutes\n`);
  
  try {
    // Step 1: Load keys from JSON file
    console.log('Step 1: Loading keys from file...');
    const keys = loadKeys(config.keysFile);
    console.log('✓ Keys loaded successfully\n');
    
    // Step 2: Detect key algorithm
    console.log('Step 2: Detecting key algorithm...');
    const algorithm = detectKeyAlgorithm(keys.privateKey);
    console.log(`✓ Detected algorithm: ${algorithm}\n`);
    
    // Step 3: Encode public key to base64
    console.log('Step 3: Encoding public key to base64...');
    const base64PublicKey = encodePublicKeyToBase64(keys.publicKey);
    console.log('✓ Public key encoded\n');
    
    // Step 4: Get Knox API base URL
    const baseUrl = getKnoxApiBaseUrl(config.region);
    console.log(`Step 4: Knox API URL: ${baseUrl}\n`);
    
    // Step 5: Generate client identifier JWT
    console.log('Step 5: Generating client identifier JWT...');
    const clientIdentifierJwt = generateClientIdentifierJwt(keys.privateKey, config.clientId, baseUrl, algorithm);
    console.log('✓ Client identifier JWT generated\n');
    
    // Decode and display JWT claims for verification
    const decodedJwt = jwt.decode(clientIdentifierJwt, { complete: true });
    console.log('JWT Header:');
    console.log(`  alg: ${decodedJwt.header.alg}`);
    console.log(`  typ: ${decodedJwt.header.typ}\n`);
    console.log('JWT Claims:');
    console.log(`  iss: ${decodedJwt.payload.iss}`);
    console.log(`  aud: ${decodedJwt.payload.aud}`);
    console.log(`  sub: ${decodedJwt.payload.sub}`);
    console.log(`  iat: ${decodedJwt.payload.iat} (${new Date(decodedJwt.payload.iat * 1000).toISOString()})`);
    console.log(`  exp: ${decodedJwt.payload.exp} (${new Date(decodedJwt.payload.exp * 1000).toISOString()})`);
    console.log(`  jti: ${decodedJwt.payload.jti}`);
    console.log(`  clientIdentifier: ${decodedJwt.payload.clientIdentifier}\n`);
    
    // Step 6: Request access token from Knox API
    console.log('Step 6: Requesting access token from Knox API...');
    const apiResponse = await requestAccessToken(baseUrl, base64PublicKey, clientIdentifierJwt);
    console.log('✓ Access token received from Knox API\n');
    
    // Step 7: Generate signed access token JWT
    console.log('Step 7: Generating signed access token JWT...');
    const signedAccessTokenJwt = generateSignedAccessTokenJwt(
      keys.privateKey,
      apiResponse.accessToken,
      config.clientId,
      algorithm
    );
    console.log('✓ Signed access token JWT generated\n');
    
    // Step 8: Display results
    console.log('=== RESULTS ===\n');
    console.log('Client Identifier JWT:');
    console.log(clientIdentifierJwt);
    console.log('\nKnox API Response:');
    console.log(JSON.stringify(apiResponse, null, 2));
    console.log('\nSigned Access Token JWT:');
    console.log(signedAccessTokenJwt);
    console.log('\n=== SUCCESS ===\n');
    
    // Save results to file
    const results = {
      timestamp: new Date().toISOString(),
      clientId: config.clientId,
      region: config.region,
      clientIdentifierJwt,
      knoxApiResponse: apiResponse,
      signedAccessTokenJwt
    };
    
    const outputFile = 'knox-token-results.json';
    fs.writeFileSync(outputFile, JSON.stringify(results, null, 2));
    console.log(`Results saved to ${outputFile}\n`);
    
  } catch (error) {
    console.error('\n=== ERROR ===\n');
    console.error(error.message);
    console.error('\nPlease check your configuration and try again.\n');
    process.exit(1);
  }
}

// Run the main function
main();
