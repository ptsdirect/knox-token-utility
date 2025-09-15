# Trust Domain Configuration

This document describes the enhanced trust domain configuration support added to knox-token-utility.

## Overview

The Knox Token Utility now supports multiple trust domains and artifact metadata configuration. This allows secure communication with both the primary Samsung Knox domain and a secondary trust domain (`trust.mdttee.com`).

## Features

### Multiple Trust Domains

The utility now supports:
- **Primary Domain**: `https://api.samsungknox.com` (Samsung Knox API)
- **Secondary Domain**: `https://trust.mdttee.com` (MDTTEE trust domain)

### Artifact Metadata

Support for artifact metadata with the format:
```
artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa
```

Components:
- **artifact**: Artifact coordinates (`com.mdttee.knox:pts`)
- **sig**: Signature method (`GPG`)
- **sbom**: SBOM formats (`cyclonedx,spdx`)
- **prov**: Provenance method (`slsa`)

## Configuration

### Environment Variables

Add these to your `.env` file or environment:

```bash
# Primary Knox API base URL (default: https://api.samsungknox.com/kcs/v1)
KNOX_API_BASE_URL=https://api.samsungknox.com/kcs/v1

# Secondary trust domain for MDTTEE (trust.mdttee.com)
KNOX_SECONDARY_TRUST_DOMAIN=https://trust.mdttee.com

# Artifact metadata configuration
KNOX_ARTIFACT_METADATA=artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa
```

### Programmatic Configuration

```java
import com.samsung.knoxwsm.token.TrustDomainConfig;
import com.samsung.knoxwsm.token.KnoxAuthClient;

// Create default configuration
TrustDomainConfig config = TrustDomainConfig.createDefault();

// Or create custom configuration
List<String> domains = List.of(
    "https://api.samsungknox.com",
    "https://trust.mdttee.com"
);
String metadata = "artifact=com.mdttee.knox:pts;sig=GPG;sbom=cyclonedx,spdx;prov=slsa";
TrustDomainConfig customConfig = TrustDomainConfig.create(domains, metadata);

// Use with Knox Auth Client
KnoxAuthClient client = new KnoxAuthClient("https://api.samsungknox.com/kcs/v1", config);
```

## Usage Examples

### Basic Usage

```java
// Create client with default trust configuration
KnoxAuthClient client = new KnoxAuthClient();

// Get trust configuration
TrustDomainConfig trustConfig = client.getTrustConfig();
System.out.println("Primary: " + trustConfig.getPrimaryTrustDomain());
System.out.println("Secondary: " + trustConfig.getSecondaryTrustDomain());

// Validate URLs
boolean trusted = client.isTrustedUrl("https://trust.mdttee.com/api");
```

### Secondary Domain Access

```java
// Request token from secondary domain
Map<String, Object> response = client.requestAccessTokenFromSecondaryDomain(
    publicKey, jwt, 30
);
```

### Artifact Metadata Parsing

```java
TrustDomainConfig.ArtifactMetadata metadata = trustConfig.parseArtifactMetadata();
System.out.println("Artifact: " + metadata.getArtifact());
System.out.println("Signature: " + metadata.getSignature());
System.out.println("SBOM Formats: " + metadata.getSbomFormats());
System.out.println("Provenance: " + metadata.getProvenance());
```

## Security Considerations

1. **Domain Validation**: Only configured trust domains are accepted
2. **Metadata Headers**: Artifact metadata is included in all API requests
3. **Configuration**: Trust domains should be carefully validated before configuration
4. **Environment Variables**: Sensitive configuration should be stored securely

## HTTP Headers

The following headers are automatically added to API requests:

- `X-KNOX-API-VERSION`: API version
- `X-Artifact-Metadata`: Artifact metadata string

## Testing

Run the trust domain tests:

```bash
mvn test -Dtest=TrustDomainConfigTest
```

Run the demo:

```bash
java -cp target/classes com.samsung.knoxwsm.token.TrustDomainDemo
```

## API Reference

### TrustDomainConfig

- `createDefault()`: Create default configuration
- `create(domains, metadata)`: Create custom configuration
- `getPrimaryTrustDomain()`: Get primary domain
- `getSecondaryTrustDomain()`: Get secondary domain
- `isTrustedDomain(url)`: Validate domain trust
- `parseArtifactMetadata()`: Parse metadata components

### KnoxAuthClient Enhancements

- `getTrustConfig()`: Get trust configuration
- `isTrustedUrl(url)`: Validate URL trust
- `getArtifactMetadata()`: Get metadata string
- `requestAccessTokenFromSecondaryDomain()`: Use secondary domain