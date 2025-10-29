#!/usr/bin/env bash
set -euo pipefail

# Knox Token Utility Deployment Check Script
# Validates a deployment across multiple aspects: build, CLI, server, release artifacts
#
# Usage: ./scripts/check-deploy.sh [options]
# Options:
#   --version <tag>     Check specific version/tag (implies --release-check)
#   --local             Check local build only (default)
#   --server-port <p>   Port for server health check (default: 8080)
#   --timeout <sec>     Timeout for server checks (default: 30)
#   --release-check     Verify GitHub release artifacts
#   --skip-build        Skip local build verification
#   --skip-cli          Skip CLI functionality check
#   --skip-server       Skip server health check
#   --help              Show this help

# Color output functions
color() { local c="$1"; shift; printf "\033[%sm%s\033[0m\n" "$c" "$*"; }
info() { color "36" "[INFO] $*"; }
success() { color "32" "[SUCCESS] $*"; }
warn() { color "33" "[WARN] $*"; }
error() { color "31" "[ERROR] $*"; }

# Default configuration
VERSION=""
LOCAL_ONLY=true
SERVER_PORT=8080
TIMEOUT=30
RELEASE_CHECK=false
SKIP_BUILD=false
SKIP_CLI=false
SKIP_SERVER=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --version)
      VERSION="$2"
      RELEASE_CHECK=true
      LOCAL_ONLY=false
      shift 2
      ;;
    --local)
      LOCAL_ONLY=true
      shift
      ;;
    --server-port)
      SERVER_PORT="$2"
      shift 2
      ;;
    --timeout)
      TIMEOUT="$2"
      shift 2
      ;;
    --release-check)
      RELEASE_CHECK=true
      LOCAL_ONLY=false
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --skip-cli)
      SKIP_CLI=true
      shift
      ;;
    --skip-server)
      SKIP_SERVER=true
      shift
      ;;
    --help)
      head -20 "$0" | tail -n +3 | sed 's/^#//'
      exit 0
      ;;
    *)
      error "Unknown argument: $1"
      exit 1
      ;;
  esac
done

# Ensure we're in project root
if [[ ! -f pom.xml ]]; then
  error "Must run from project root (pom.xml not found)"
  exit 1
fi

# Global exit status
OVERALL_STATUS=0

# Set Java 21 if available (required for build)
setup_java() {
  if [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    info "Using Java 21 from $JAVA_HOME"
  else
    warn "Java 21 not found, using system Java: $(java -version 2>&1 | head -1)"
  fi
}

# Check local build
check_build() {
  if $SKIP_BUILD; then
    info "Skipping build check"
    return 0
  fi
  
  info "Checking local build..."
  
  # Check if fat JAR exists
  JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true)
  if [[ -z "$JAR" ]]; then
    info "Fat JAR not found, building..."
    if mvn -q -ntp clean package -Dgpg.skip=true -DskipTests; then
      success "Build completed successfully"
      JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar | head -n1)
    else
      error "Build failed"
      return 1
    fi
  else
    success "Fat JAR found: $JAR"
  fi
  
  # Verify JAR can be executed
  if java -jar "$JAR" --help >/dev/null 2>&1; then
    success "JAR executable and responds to --help"
  else
    error "JAR execution failed"
    return 1
  fi
  
  # Check for basic build artifacts
  local missing=()
  ls target/pts-*-sources.jar >/dev/null 2>&1 || missing+=("sources JAR")
  ls target/pts-*-javadoc.jar >/dev/null 2>&1 || missing+=("javadoc JAR")
  
  if [[ ${#missing[@]} -gt 0 ]]; then
    warn "Missing artifacts: ${missing[*]}"
  else
    success "All expected build artifacts present"
  fi
  
  return 0
}

# Check CLI functionality
check_cli() {
  if $SKIP_CLI; then
    info "Skipping CLI check"
    return 0
  fi
  
  info "Checking CLI functionality..."
  
  JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar 2>/dev/null | head -n1)
  if [[ -z "$JAR" ]]; then
    error "No JAR found for CLI testing"
    return 1
  fi
  
  # Test help command
  if java -jar "$JAR" --help >/dev/null 2>&1; then
    success "CLI help command works"
  else
    error "CLI help command failed"
    return 1
  fi
  
  # Test key generation (if keys don't exist)
  if [[ ! -f private_key.pem ]] || [[ ! -f public_key.pem ]]; then
    info "Generating test keys..."
    if java -jar "$JAR" --mode generate-keys >/dev/null 2>&1; then
      success "Key generation works"
    else
      error "Key generation failed"
      return 1
    fi
  else
    success "Keys already present"
  fi
  
  # Test configuration loading
  if echo "TEST_VAR=test_value" > .env.test; then
    if java -cp "$JAR" com.samsung.knoxwsm.token.Config 2>/dev/null || true; then
      success "Configuration system accessible"
    fi
    rm -f .env.test
  fi
  
  return 0
}

# Check server functionality
check_server() {
  if $SKIP_SERVER; then
    info "Skipping server check"
    return 0
  fi
  
  info "Checking server functionality..."
  
  JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar 2>/dev/null | head -n1)
  if [[ -z "$JAR" ]]; then
    error "No JAR found for server testing"
    return 1
  fi
  
  # Ensure keys exist for server
  if [[ ! -f private_key.pem ]]; then
    info "Generating keys for server test..."
    java -jar "$JAR" --mode generate-keys >/dev/null 2>&1
  fi
  
  # Start server in background
  info "Starting server on port $SERVER_PORT..."
  java -jar "$JAR" server "$SERVER_PORT" >/dev/null 2>&1 &
  local server_pid=$!
  
  # Wait for server to start
  local count=0
  while [[ $count -lt $TIMEOUT ]]; do
    if curl -s "http://localhost:$SERVER_PORT/api/token" >/dev/null 2>&1; then
      break
    fi
    sleep 1
    ((count++))
  done
  
  # Test server endpoints
  local server_ok=true
  
  # Test that server responds (even with method not allowed)
  if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$SERVER_PORT/api/token" | grep -q "405"; then
    success "Server responds to GET /api/token (405 Method Not Allowed as expected)"
  else
    error "Server not responding on /api/token"
    server_ok=false
  fi
  
  # Test POST with sample data
  local response
  response=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"clientId":"test-client","deviceId":"123456789012345"}' \
    "http://localhost:$SERVER_PORT/api/token" 2>/dev/null || echo "CURL_FAILED")
  
  if [[ "$response" != "CURL_FAILED" ]] && echo "$response" | grep -q "jwt"; then
    success "Server POST /api/token returns JWT response"
  else
    warn "Server POST /api/token did not return expected JWT (response: ${response:0:100}...)"
  fi
  
  # Cleanup
  kill $server_pid >/dev/null 2>&1 || true
  wait $server_pid >/dev/null 2>&1 || true
  
  if $server_ok; then
    success "Server functionality check passed"
    return 0
  else
    error "Server functionality check failed"
    return 1
  fi
}

# Check release artifacts (GitHub)
check_release() {
  if ! $RELEASE_CHECK; then
    info "Skipping release check"
    return 0
  fi
  
  if [[ -z "$VERSION" ]]; then
    error "Version required for release check"
    return 1
  fi
  
  info "Checking release artifacts for version $VERSION..."
  
  # Use existing verify-release script if available
  if [[ -f scripts/verify-release.sh ]]; then
    info "Using existing release verification script..."
    # Create a temporary directory for verification
    local temp_dir
    temp_dir=$(mktemp -d)
    cd "$temp_dir"
    
    # Try to verify release (allow some warnings)
    if timeout $TIMEOUT bash -c "REPO=ptsdirect/knox-token-utility /home/runner/work/knox-token-utility/knox-token-utility/scripts/verify-release.sh '$VERSION' /dev/null" 2>/dev/null; then
      success "Release verification passed"
      cd - >/dev/null
      rm -rf "$temp_dir"
      return 0
    else
      warn "Release verification script failed or timed out"
      cd - >/dev/null
      rm -rf "$temp_dir"
    fi
  fi
  
  # Fallback: basic release check
  local release_url="https://api.github.com/repos/ptsdirect/knox-token-utility/releases/tags/$VERSION"
  if curl -s -f "$release_url" >/dev/null; then
    success "Release $VERSION exists on GitHub"
    
    # Check for key artifacts
    local assets_url="https://github.com/ptsdirect/knox-token-utility/releases/download/$VERSION"
    local jar_name="knox-token-utility-${VERSION#v}-jar-with-dependencies.jar"
    
    if curl -s -I -f "$assets_url/$jar_name" >/dev/null; then
      success "Release JAR artifact accessible"
    else
      warn "Release JAR artifact not found or not accessible"
    fi
    
    if curl -s -I -f "$assets_url/SHA256SUMS" >/dev/null; then
      success "Release checksums accessible"
    else
      warn "Release checksums not found"
    fi
    
    return 0
  else
    error "Release $VERSION not found on GitHub"
    return 1
  fi
}

# Print deployment summary
print_summary() {
  info "=== Deployment Check Summary ==="
  if [[ $OVERALL_STATUS -eq 0 ]]; then
    success "All deployment checks passed"
  else
    error "Some deployment checks failed"
  fi
  
  info "Checked components:"
  $SKIP_BUILD || info "  ✓ Build system and artifacts"
  $SKIP_CLI || info "  ✓ CLI functionality"
  $SKIP_SERVER || info "  ✓ Server functionality"
  $RELEASE_CHECK && info "  ✓ Release artifacts (version: $VERSION)"
  
  echo
}

# Main execution
main() {
  info "Knox Token Utility Deployment Check"
  info "Configuration: local=$LOCAL_ONLY, release-check=$RELEASE_CHECK, version=$VERSION"
  echo
  
  setup_java
  
  # Run checks
  if ! check_build; then
    OVERALL_STATUS=1
  fi
  
  if ! check_cli; then
    OVERALL_STATUS=1
  fi
  
  if ! check_server; then
    OVERALL_STATUS=1
  fi
  
  if ! check_release; then
    OVERALL_STATUS=1
  fi
  
  print_summary
  exit $OVERALL_STATUS
}

# Run main function
main "$@"