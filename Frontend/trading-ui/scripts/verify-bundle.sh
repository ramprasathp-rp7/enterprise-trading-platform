#!/bin/bash
# Secret-Free Bundle Verification Script
# Run this before any production release to ensure no secrets are in the bundle

set -e

BUNDLE_DIR="dist/sprint-09-trading-ui"
FAILED=0

echo "=========================================="
echo "Secret-Free Bundle Verification"
echo "=========================================="
echo ""

# Check if bundle exists
if [ ! -d "$BUNDLE_DIR" ]; then
  echo "❌ Bundle not found at $BUNDLE_DIR"
  echo "Run: npm run build"
  exit 1
fi

echo "Checking bundle for secret patterns..."
echo ""

# Define patterns to search for
PATTERNS=(
  "x-api-key"
  "api_key"
  "api-key"
  "fauxnance"
  "execute-api.*amazonaws"
  "jwt_secret"
  "JWT_SECRET"
)

# Search for each pattern
for pattern in "${PATTERNS[@]}"; do
  echo "Searching for: $pattern"
  if grep -r "$pattern" "$BUNDLE_DIR" --exclude="*.map" 2>/dev/null; then
    echo "❌ Found '$pattern' in bundle!"
    FAILED=1
  else
    echo "✅ No '$pattern' found"
  fi
  echo ""
done

# Check for common secret file patterns (exclude source maps which are normal)
echo "Checking for secret file patterns..."
SECRET_FILES=$(find "$BUNDLE_DIR" -type f \( -name "*.key" -o -name "*.pem" -o -name "*.secret" \) 2>/dev/null | grep -v "\.map")
if [ -n "$SECRET_FILES" ]; then
  echo "❌ Found secret files in bundle!"
  echo "$SECRET_FILES"
  FAILED=1
else
  echo "✅ No secret files found"
fi
echo ""

# Check bundle size is reasonable
BUNDLE_SIZE=$(du -sh "$BUNDLE_DIR" | cut -f1)
echo "Bundle size: $BUNDLE_SIZE"
echo ""

if [ $FAILED -eq 0 ]; then
  echo "=========================================="
  echo "✅ VERIFICATION PASSED"
  echo "=========================================="
  echo "Bundle is safe for production deployment"
  exit 0
else
  echo "=========================================="
  echo "❌ VERIFICATION FAILED"
  echo "=========================================="
  echo "Do NOT deploy - secrets found in bundle"
  exit 1
fi
