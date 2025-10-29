#!/usr/bin/env bash
set -euo pipefail

# Test deployment check script functionality
# This test validates that the check-deploy.sh script works correctly

cd "$(dirname "$0")/.."

echo "Testing check-deploy.sh script..."

# Test help output
echo "Testing --help option..."
if ./scripts/check-deploy.sh --help | grep -q "Usage:"; then
    echo "✓ Help option works"
else
    echo "✗ Help option failed"
    exit 1
fi

# Test invalid argument handling
echo "Testing invalid argument handling..."
output=$(./scripts/check-deploy.sh --invalid-option 2>&1 || true)
if echo "$output" | grep -q "ERROR.*Unknown argument"; then
    echo "✓ Invalid argument handling works"
else
    echo "✗ Invalid argument handling failed"
    exit 1
fi

# Test local check with skipped components
echo "Testing local check with server skipped..."
if ./scripts/check-deploy.sh --local --skip-server >/dev/null 2>&1; then
    echo "✓ Local check with server skipped works"
else
    echo "✗ Local check with server skipped failed"
    exit 1
fi

# Test that script detects missing build
echo "Testing missing build detection..."
if [ -d target ]; then
    mv target target.backup
    output=$(./scripts/check-deploy.sh --local --skip-server --skip-cli 2>&1 || true)
    if echo "$output" | grep -q "building\|not found"; then
        echo "✓ Missing build detection works"
    else
        echo "✗ Missing build detection failed"
        mv target.backup target
        exit 1
    fi
    mv target.backup target
fi

echo "All tests passed!"