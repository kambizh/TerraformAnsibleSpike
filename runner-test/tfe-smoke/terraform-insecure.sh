#!/bin/bash
# Wrapper to run Terraform with TLS verification disabled

# Export variables to disable TLS checks
export GOPROXY=direct
export GOINSECURE="*"
export CGO_ENABLED=0

# Run terraform with all arguments passed through
exec terraform "$@"