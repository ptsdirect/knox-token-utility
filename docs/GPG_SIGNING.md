# GPG Signing Setup (Tags & Future Maven Central)

This guide walks through creating and using a GPG key to sign git commits/tags and (optionally) Maven artifacts.

## 1. Install GPG
macOS (Homebrew):
```bash
brew install gnupg pinentry-mac
```
Add to your `~/.gnupg/gpg-agent.conf`:
```
pinentry-program /opt/homebrew/bin/pinentry-mac
```
Then reload:
```bash
gpgconf --kill gpg-agent
```

## 2. Generate a Key (Ed25519 or RSA 4096)
Modern (recommended):
```bash
gpg --quick-generate-key "Your Name <you@example.com>" future-default never
```
Legacy (if Ed25519 not allowed):
```bash
gpg --full-generate-key
# Type: RSA and RSA
# Size: 4096
# Expiration: 2y (extend later)
```

List keys:
```bash
gpg --list-secret-keys --keyid-format=long
```
Copy the LONG key ID (e.g. `ABCD1234EF567890`).

## 3. Export Public Key for GitHub
```bash
gpg --armor --export ABCD1234EF567890
```
Copy output → GitHub → Settings → SSH and GPG keys → New GPG Key.

## 4. Configure Git to Sign
```bash
git config --global user.signingkey ABCD1234EF567890
git config --global commit.gpgsign true
```
(Optional) Sign tags by default:
```bash
git config --global tag.gpgSign true
```

If using pinentry-mac add in `~/.zshrc`:
```bash
export GPG_TTY=$(tty)
```

## 5. Test Signing
```bash
echo test > /tmp/gpgtest.txt
git add /tmp/gpgtest.txt
git commit -m "test: gpg signing"
```
Run:
```bash
git log --show-signature -1
```
You should see `gpg: Signature made ...` and `Good signature`.

## 6. Signing Release Tags With Script
Use:
```bash
./scripts/release.sh 1.0.1 --sign
```
The script switches to `git tag -s` automatically.

## 7. CI Considerations (Optional)
If you later sign artifacts in CI (Maven Central):
1. Export private key (encrypted):
   ```bash
   gpg --armor --export-secret-keys ABCD1234EF567890 > private.asc
   ```
2. Store `private.asc` contents as GitHub secret `GPG_PRIVATE_KEY`.
3. Store passphrase as `GPG_PASSPHRASE`.
4. Import in workflow step:
   ```yaml
   - name: Import GPG key
     run: |
       echo "$GPG_PRIVATE_KEY" | gpg --batch --import
       echo "pinentry-mode loopback" >> ~/.gnupg/gpg.conf
   - name: Configure Maven GPG (if artifact signing later)
     run: |
       cat > ~/.m2/settings.xml <<'EOF'
       <settings>
         <servers>
           <server>
             <id>ossrh</id>
             <username>${env.OSSRH_USERNAME}</username>
             <password>${env.OSSRH_PASSWORD}</password>
           </server>
         </servers>
       </settings>
       EOF
   env:
     GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
     GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
   ```
5. Add Maven GPG plugin and distributionManagement when publishing to Central.

## 8. Rotating Keys
- Generate new key
- Publish new public key to GitHub
- Update `user.signingkey` and CI secrets
- Revoke old key (optional) and push revocation to key servers (if used)

## 9. Troubleshooting
| Issue | Fix |
|-------|-----|
| `Inappropriate ioctl for device` | Ensure `export GPG_TTY=$(tty)` in shell init | 
| Hanging on passphrase | Install proper pinentry + gpg-agent restart | 
| `Bad signature` on GitHub | Public key not uploaded or wrong key ID | 
| CI cannot import key | Ensure multi-line secrets preserved (GitHub Secrets auto-strips CRLF) | 

## 10. Next: Maven Artifact Signing (Future)
Add to `pom.xml` when publishing to Central:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-gpg-plugin</artifactId>
  <version>3.2.4</version>
  <executions>
    <execution>
      <id>sign-artifacts</id>
      <phase>verify</phase>
      <goals><goal>sign</goal></goals>
    </execution>
  </executions>
  <configuration>
    <gpgArguments>
      <arg>--batch</arg>
      <arg>--pinentry-mode</arg>
      <arg>loopback</arg>
    </gpgArguments>
  </configuration>
</plugin>
```
(Do not enable until distributionManagement/OSSRH credentials are set.)

---
Document created: 2025-09-14.
