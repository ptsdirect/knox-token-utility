# Knox Access Token Generator Script

## Overview

The `generateKnoxAccessToken.js` script is a comprehensive Node.js utility for generating Samsung Knox access tokens with proper JWT claims. It supports both RS256 (RSA) and ES256 (Elliptic Curve) signing algorithms, automatically detecting the appropriate algorithm based on the key type.

## Features

- ✅ Loads keys from `keys.json` file with privateKey and publicKey in PEM format
- ✅ Generates client identifier JWT with all required claims:
  - `iss` (issuer): Client identifier
  - `aud` (audience): Knox token endpoint URL
  - `sub` (subject): Client identifier
  - `iat` (issued at): Current timestamp
  - `exp` (expiration): 30 minutes from issuance
  - `jti` (JWT ID): Unique identifier (UUID)
  - `clientIdentifier`: Custom claim for client identifier
- ✅ Sends JWT to Samsung Knox API to get access token
- ✅ Generates signed access token JWT
- ✅ Supports both RS256 (RSA 2048-bit) and ES256 (EC P-256) algorithms
- ✅ Auto-detects key type and selects appropriate algorithm
- ✅ Base64 encoding of public key for API requests
- ✅ 30-minute token validity
- ✅ Comprehensive error handling and logging
- ✅ Regional endpoint support (us, eu, ap)
- ✅ Saves results to JSON file

## Prerequisites

- Node.js 18+ (as specified in package.json)
- npm dependencies installed: `jsonwebtoken`, `axios`

Install dependencies:
```bash
npm install
```

## Usage

### Basic Usage

```bash
node generateKnoxAccessToken.js
```

This uses default settings:
- Keys file: `keys.json`
- Client ID: `your-client-identifier` (or from `KNOX_CLIENT_ID` env var)
- Region: `us` (or from `KNOX_REGION` env var)

### With Command Line Arguments

```bash
node generateKnoxAccessToken.js --keys-file=keys.json --client-id=my-client-123 --region=us
```

### With Environment Variables

```bash
export KNOX_CLIENT_ID="my-client-identifier"
export KNOX_REGION="eu"
node generateKnoxAccessToken.js
```

## Key File Format

The script requires a `keys.json` file with the following structure:

```json
{
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
}
```

See `keys.json.example` for a complete example.

### Generating Keys

#### Option 1: Using the Java Tool (ES256 / EC P-256)

```bash
# Build the Java tool
mvn clean package -Dgpg.skip=true -DskipTests

# Generate EC P-256 keys
java -jar target/pts-*-jar-with-dependencies.jar --mode generate-keys

# Create keys.json from generated PEM files
node -e "
const fs = require('fs');
const privateKey = fs.readFileSync('private_key.pem', 'utf8');
const publicKey = fs.readFileSync('public_key.pem', 'utf8');
fs.writeFileSync('keys.json', JSON.stringify({ privateKey, publicKey }, null, 2));
"
```

#### Option 2: Using OpenSSL (RS256 / RSA 2048)

```bash
# Generate RSA private key
openssl genrsa -out private_key.pem 2048

# Extract public key
openssl rsa -in private_key.pem -pubout -out public_key.pem

# Create keys.json
node -e "
const fs = require('fs');
const privateKey = fs.readFileSync('private_key.pem', 'utf8');
const publicKey = fs.readFileSync('public_key.pem', 'utf8');
fs.writeFileSync('keys.json', JSON.stringify({ privateKey, publicKey }, null, 2));
"
```

## Configuration Options

### Command Line Arguments

- `--keys-file=<path>`: Path to keys JSON file (default: `keys.json`)
- `--client-id=<id>`: Client identifier for JWT claims (default: from env or `your-client-identifier`)
- `--region=<us|eu|ap>`: Knox API region (default: `us`)

### Environment Variables

- `KNOX_CLIENT_ID`: Client identifier (overridden by `--client-id`)
- `KNOX_REGION`: Knox API region: `us`, `eu`, or `ap` (overridden by `--region`)
- `KNOX_API_BASE_URL`: Override the base URL for Knox API (advanced usage)

## Regional Endpoints

The script automatically resolves Knox API endpoints based on the region:

- `us` (default): `https://us-api.samsungknox.com/kcs/v1`
- `eu`: `https://eu-api.samsungknox.com/kcs/v1`
- `ap`: `https://ap-api.samsungknox.com/kcs/v1`

## Output

The script provides detailed step-by-step output:

```
=== Samsung Knox Access Token Generator ===

Keys File: keys.json
Client ID: test-client-123
Region: us
Token Validity: 30 minutes

Step 1: Loading keys from file...
✓ Keys loaded successfully

Step 2: Detecting key algorithm...
✓ Detected algorithm: ES256

Step 3: Encoding public key to base64...
✓ Public key encoded

Step 4: Knox API URL: https://us-api.samsungknox.com/kcs/v1

Step 5: Generating client identifier JWT...
✓ Client identifier JWT generated

JWT Header:
  alg: ES256
  typ: JWT

JWT Claims:
  iss: test-client-123
  aud: https://us-api.samsungknox.com/kcs/v1/ses/token
  sub: test-client-123
  iat: 1762408725 (2025-11-06T05:58:45.000Z)
  exp: 1762410525 (2025-11-06T06:28:45.000Z)
  jti: 460a9cb5-dd3e-4a2b-ad79-513c62957d34
  clientIdentifier: test-client-123

Step 6: Requesting access token from Knox API...
✓ Access token received from Knox API

Step 7: Generating signed access token JWT...
✓ Signed access token JWT generated

=== RESULTS ===

Client Identifier JWT:
eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9...

Knox API Response:
{
  "accessToken": "...",
  "refreshToken": "...",
  "expiresIn": 1800
}

Signed Access Token JWT:
eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9...

=== SUCCESS ===

Results saved to knox-token-results.json
```

### Output Files

- `knox-token-results.json`: Complete results including all tokens and API response

## Error Handling

The script includes comprehensive error handling:

- Missing keys file: Clear error message with instructions
- Invalid JSON: Syntax error details
- Missing key fields: Validation error
- API request failures: HTTP status code and response data
- Network errors: Connection failure messages

## Algorithm Detection

The script automatically detects the key type:

- **ES256**: Used for EC P-256 keys (recommended by Samsung Knox)
  - Key size: ~150 base64 characters
  - Header: `BEGIN EC PRIVATE KEY` or short `BEGIN PRIVATE KEY`
  
- **RS256**: Used for RSA 2048-bit keys
  - Key size: ~1700 base64 characters
  - Header: `BEGIN RSA PRIVATE KEY` or long `BEGIN PRIVATE KEY`

## Security Considerations

- Never commit `keys.json` or key files to version control
- Store keys securely (use environment variables or secret management in production)
- Rotate keys regularly
- Use appropriate file permissions (chmod 600) on key files
- The generated tokens are valid for 30 minutes

## Integration with Existing Scripts

This script complements the existing Node.js scripts in the repository:

- `generateKnoxJWT.js`: Simple JWT generation (no API call)
- `getKnoxAccessToken.js`: Basic access token request
- `getKnoxAccessTokenFull.js`: Similar functionality with less structure
- `generateKnoxAccessToken.js` (this script): **Comprehensive solution with all features**

## Troubleshooting

### Module Warning

If you see: `Warning: Module type of file:///...generateKnoxAccessToken.js is not specified`

This is harmless, but you can eliminate it by adding to `package.json`:
```json
{
  "type": "module"
}
```

### API Connection Errors

If the Knox API is unreachable:
- Verify your network connection
- Check if your IP is whitelisted with Samsung Knox
- Verify the region setting matches your Knox account
- Ensure client ID is registered with Knox

### Key Format Errors

If you get key format errors:
- Ensure keys are in PEM format
- Verify the JSON structure matches the expected format
- Check that newlines in keys are properly escaped in JSON

## Related Documentation

- See `README.md` for the main project documentation
- See `USAGE.md` for comprehensive usage examples
- See scripts directory for Bash automation workflows
- See Java `TokenClient` for equivalent Java implementation
