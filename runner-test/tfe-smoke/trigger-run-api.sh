#!/bin/bash
set -euo pipefail

# Script to trigger a Terraform Run via TFE API
# This bypasses terraform CLI TLS certificate issues

TFE_HOST="app.terraform.io"
ORG="kambiz_test_dev_spike"
WORKSPACE="test-api-driven"
TOKEN=$(grep -oP '(?<="token": ")[^"]*' ~/.terraform.d/credentials.tfrc.json)
KEEP_TARBALL="${KEEP_TARBALL:-0}"
KEEP_TARBALL=1

echo "=== Triggering Terraform Run via API ==="
echo "Organization: $ORG"
echo "Workspace: $WORKSPACE"
echo ""

# Get workspace ID
echo "1. Fetching workspace ID..."
WORKSPACE_JSON=$(curl -sk \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://$TFE_HOST/api/v2/organizations/$ORG/workspaces/$WORKSPACE")

WORKSPACE_ID=$(echo "$WORKSPACE_JSON" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['id'])")

if [ -z "$WORKSPACE_ID" ]; then
  echo "ERROR: Could not fetch workspace ID"
  exit 1
fi

echo "   Workspace ID: $WORKSPACE_ID"
echo ""

# Create a configuration version
echo "2. Creating configuration version..."
CONFIG_RESPONSE=$(curl -sk \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  --request POST \
  --data '{
    "data": {
      "type": "configuration-versions",
      "attributes": {
        "auto-queue-runs": false
      }
    }
  }' \
  "https://$TFE_HOST/api/v2/workspaces/$WORKSPACE_ID/configuration-versions")

UPLOAD_URL=$(echo "$CONFIG_RESPONSE" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['attributes'].get('upload-url', ''))")
CONFIG_VERSION_ID=$(echo "$CONFIG_RESPONSE" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['id'])")

echo "uploadURL is $UPLOAD_URL"
echo "configVersionID is $CONFIG_VERSION_ID"

if [ -z "$UPLOAD_URL" ] || [ -z "$CONFIG_VERSION_ID" ]; then
  echo "ERROR: Could not create configuration version"
  echo "$CONFIG_RESPONSE"
  exit 1
fi

echo "   Config Version ID: $CONFIG_VERSION_ID"
echo ""

# Create a tar.gz of the current directory with timestamp
echo "3. Packaging Terraform configuration..."
UPLOAD_FILE_NAME="./content-$(date +%s).tar.gz"
CONTENT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Create tarball - configuration directory must be root of tar file
tar -zcvf "$UPLOAD_FILE_NAME" -C "$CONTENT_DIR" \
  --exclude='.terraform' \
  --exclude='.git' \
  --exclude='*.sh' \
  --exclude='main-cloud-backup.tf' \
  --exclude='SETUP.md' \
  --exclude='README.md' \
  main.tf

echo "   Package created: $UPLOAD_FILE_NAME in $CONTENT_DIR"
echo ""

# Upload the configuration
echo "4. Uploading configuration..."
UPLOAD_STATUS=$(curl -sk \
  --header "Content-Type: application/octet-stream" \
  --request PUT \
  --data-binary @"$UPLOAD_FILE_NAME" \
  --write-out "%{http_code}" \
  --output /dev/null \
  "$UPLOAD_URL")

if [ "$UPLOAD_STATUS" != "200" ]; then
  echo "ERROR: Upload failed with status $UPLOAD_STATUS"
  rm -f "$UPLOAD_FILE_NAME"
  exit 1
fi

echo "   Upload successful (HTTP $UPLOAD_STATUS)"
echo ""

# Clean up tarball (optional)
if [ "$KEEP_TARBALL" = "1" ]; then
  echo "   KEEP_TARBALL=1 set; leaving tarball at: $CONTENT_DIR/$UPLOAD_FILE_NAME"
else
  rm -f "$UPLOAD_FILE_NAME"
fi

# Wait for config to be processed
echo "5. Waiting for configuration to be processed..."
MAX_WAIT=60
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
  CONFIG_STATUS=$(curl -sk \
    --header "Authorization: Bearer $TOKEN" \
    "https://$TFE_HOST/api/v2/configuration-versions/$CONFIG_VERSION_ID" \
    | grep -oP '"status":"\K[^"]+' | head -1)
  
  echo "   Status: $CONFIG_STATUS (waited ${WAITED}s)"
  
  if [ "$CONFIG_STATUS" = "uploaded" ]; then
    echo "   Configuration ready!"
    break
  fi
  
  sleep 3
  WAITED=$((WAITED + 3))
done

if [ "$CONFIG_STATUS" != "uploaded" ]; then
  echo "   WARNING: Configuration status is still $CONFIG_STATUS after ${WAITED}s"
  echo "   Ingress details (if present):"
  curl -sk \
    --header "Authorization: Bearer $TOKEN" \
    "https://$TFE_HOST/api/v2/configuration-versions/$CONFIG_VERSION_ID" \
    | python3 -c "import json, sys; data=json.load(sys.stdin); attrs=data.get('data',{}).get('attributes',{}); print(json.dumps({'status': attrs.get('status'), 'error': attrs.get('error'), 'error-message': attrs.get('error-message'), 'ingress-attributes': attrs.get('ingress-attributes')}, indent=2))"
  echo "   Attempting to create run anyway (may fail while config is pending)..."
fi
echo ""

# Create a run manually
echo "6. Creating run..."
RUN_RESPONSE=$(curl -sk \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  --request POST \
  --data "{
    \"data\": {
      \"type\": \"runs\",
      \"attributes\": {
        \"message\": \"Smoke test run to trigger post-apply Run Task\",
        \"auto-apply\": false
      },
      \"relationships\": {
        \"configuration-version\": {
          \"data\": {
            \"type\": \"configuration-versions\",
            \"id\": \"$CONFIG_VERSION_ID\"
          }
        },
        \"workspace\": {
          \"data\": {
            \"type\": \"workspaces\",
            \"id\": \"$WORKSPACE_ID\"
          }
        }
      }
    }
  }" \
  "https://$TFE_HOST/api/v2/runs")

RUN_ID=$(echo "$RUN_RESPONSE" | grep -oP '"id":"run-\K[^"]+' | head -1 | sed 's/^/run-/')

if [ -z "$RUN_ID" ]; then
  echo "ERROR: Could not create run"
  echo "$RUN_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RUN_RESPONSE"
  exit 1
fi

echo "   Run ID: $RUN_ID"
echo ""

# Wait for the run to start
sleep 2

echo "=== Run Triggered Successfully ==="
echo ""
echo "View run in TFE:"
echo "https://$TFE_HOST/app/$ORG/workspaces/$WORKSPACE/runs/$RUN_ID"
echo ""
echo "The post-apply Run Task should execute after the apply completes."
echo ""
