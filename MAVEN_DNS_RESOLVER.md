# Maven DNS Resolver

The Knox Token Utility now includes a DNS TXT record resolver for Maven artifact discovery. This feature allows resolving Maven artifact information from DNS TXT records.

## Supported TXT Record Format

The resolver supports TXT records with the following format:
```
groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg
```

### Required Fields
- `groupid`: Maven group ID (e.g., `com.mdttee.knox`)
- `artifactid`: Maven artifact ID (e.g., `pts`)

### Optional Fields
- `repo`: Repository name (e.g., `central`)
- `sig`: Signature or verification token (e.g., `gg`)

## Usage

### Command Line Interface

Resolve Maven artifact from DNS TXT record:
```bash
java -jar knox-token-utility.jar --resolve-maven pts._maven.example.com
```

Example output when TXT record is found:
```
Maven artifact resolved successfully:
  Group ID: com.mdttee.knox
  Artifact ID: pts
  Repository: central
  Signature: gg
  Maven coordinates: com.mdttee.knox:pts
```

### Programmatic Usage

```java
import com.samsung.knoxwsm.token.MavenDnsResolver;

MavenDnsResolver resolver = new MavenDnsResolver();
MavenDnsResolver.MavenArtifact artifact = resolver.resolveMavenArtifact("pts._maven.example.com");

if (artifact != null) {
    System.out.println("Group ID: " + artifact.getGroupId());
    System.out.println("Artifact ID: " + artifact.getArtifactId());
    System.out.println("Repository: " + artifact.getRepository());
    System.out.println("Signature: " + artifact.getSignature());
}
```

## DNS Setup Example

To set up DNS TXT records in Cloudflare or other DNS providers:

1. Create a TXT record with name: `pts._maven`
2. Set the content: `groupid=com.mdttee.knox;artifactid=pts;repo=central;sig=gg`

The resolver will query the full hostname (e.g., `pts._maven.yourdomain.com`) and parse the TXT record content.

## Error Handling

The resolver gracefully handles:
- DNS resolution failures (returns `null`)
- Missing or malformed TXT records (returns `null`)
- Missing required fields (returns `null`)
- Network connectivity issues (returns `null`)

## Integration

The DNS resolver is integrated into the Knox Token Utility and demonstrates its usage in the default demo mode. It can be used standalone or as part of larger Maven repository discovery systems.