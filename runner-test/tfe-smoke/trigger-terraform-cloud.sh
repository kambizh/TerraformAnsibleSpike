#!/bin/bash
set -euo pipefail

# Script to trigger a Terraform Run via Terraform Cloud (app.terraform.io) API
# Configured for your workspace: kambiz_test_dev_spike/test-api-driven
# Post-apply Run Task endpoint: http://ec2-51-21-180-41.eu-north-1.compute.amazonaws.com:8080/api/run-task

TFE_HOST="app.terraform.io"
ORG="kambiz_test_dev_spike"
WORKSPACE="test-api-driven"
TOKEN=$(grep -oP '(?<="token": ")[^"]*' ~/.terraform.d/credentials.tfrc.json | tail -1)
KEEP_TARBALL="${KEEP_TARBALL:-0}"

echo "=== Triggering Terraform Run via Terraform Cloud API ==="
echo "Organization: $ORG"
echo "Workspace: $WORKSPACE"
echo "Host: $TFE_HOST"
echo ""

# Get workspace ID
echo "1. Fetching workspace ID..."
WORKSPACE_JSON=$(curl -s \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://$TFE_HOST/api/v2/organizations/$ORG/workspaces/$WORKSPACE")

WORKSPACE_ID=$(echo "$WORKSPACE_JSON" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['id'])")

if [ -z "$WORKSPACE_ID" ]; then
  echo "ERROR: Could not fetch workspace ID"
  echo "$WORKSPACE_JSON" | python3 -m json.tool
  exit 1
fi

echo "   Workspace ID: $WORKSPACE_ID"
echo ""

# Create a configuration version with auto-queue enabled
echo "2. Creating configuration version (auto-queue)..."
CONFIG_RESPONSE=$(curl -s \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  --request POST \
  --data '{
    "data": {
      "type": "configuration-versions",
      "attributes": {
        "auto-queue-runs": true
      }
    }
  }' \
  "https://$TFE_HOST/api/v2/workspaces/$WORKSPACE_ID/configuration-versions")

UPLOAD_URL=$(echo "$CONFIG_RESPONSE" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['attributes'].get('upload-url', ''))")
CONFIG_VERSION_ID=$(echo "$CONFIG_RESPONSE" | python3 -c "import json, sys; data=json.load(sys.stdin); print(data['data']['id'])")

if [ -z "$UPLOAD_URL" ] || [ -z "$CONFIG_VERSION_ID" ]; then
  echo "ERROR: Could not create configuration version"
  echo "$CONFIG_RESPONSE" | python3 -m json.tool
  exit 1
fi

echo "   Config Version ID: $CONFIG_VERSION_ID"
echo ""

# Create a tar.gz of the current directory with timestamp
echo "3. Packaging Terraform configuration..."
UPLOAD_FILE_NAME="./content-$(date +%s).tar.gz"
CONTENT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Create tarball - configuration directory must be root of tar file
tar -zcf "$UPLOAD_FILE_NAME" -C "$CONTENT_DIR" \
  --exclude='.terraform' \
  --exclude='.git' \
  --exclude='*.sh' \
  --exclude='*.md' \
  --exclude='*.tar.gz' \
  --exclude='content' \
  --exclude='contentForApi' \
  --exclude='kk' \
  main.tf

echo "   Package created: $UPLOAD_FILE_NAME"
echo ""

# Upload the configuration
echo "4. Uploading configuration..."
UPLOAD_STATUS=$(curl -s \
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
  echo "   Keeping tarball: $UPLOAD_FILE_NAME"
else
  rm -f "$UPLOAD_FILE_NAME"
fi

# Auto-queue will create the run automatically
echo "5. Waiting for auto-queue to create run (10s)..."
sleep 10

# Find the run
RUNS_JSON=$(curl -s \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/vnd.api+json" \
  "https://$TFE_HOST/api/v2/workspaces/$WORKSPACE_ID/runs?page%5Bsize%5D=1")

RUN_ID=$(echo "$RUNS_JSON" | python3 -c "import json, sys; data=json.load(sys.stdin); items=data.get('data',[]); print(items[0]['id'] if items else '')")

if [ -z "$RUN_ID" ]; then
  echo "   No run found yet. Check Terraform Cloud UI."
  echo ""
  echo "View workspace runs:"
  echo "https://$TFE_HOST/app/$ORG/workspaces/$WORKSPACE/runs"
else
  echo "   Run ID: $RUN_ID"
  echo ""
  echo "=== Run Created Successfully ==="
  echo ""
  echo "View run in Terraform Cloud:"
  echo "https://$TFE_HOST/app/$ORG/workspaces/$WORKSPACE/runs/$RUN_ID"
fi

echo ""
echo "🚀 The post-apply Run Task will trigger your EC2 endpoint after apply:"
echo "   http://ec2-51-21-180-41.eu-north-1.compute.amazonaws.com:8080/api/run-task"
echo ""
echo "Check the 'Run Tasks' section in the Terraform Cloud run details."
echo ""
