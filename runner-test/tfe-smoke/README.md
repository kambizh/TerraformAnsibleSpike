# TFE Run Task Smoke Test (API-Driven Runs)

This folder is a small “smoke” Terraform configuration plus scripts that trigger a Terraform Enterprise run via the TFE API. The purpose is to validate that a **post-apply Run Task** calls your Java endpoint and triggers Ansible.

## Prerequisites
- Access to `https://ech-10-157-151-43.mastercard.int`
- TFE org `kambiz` and workspace `RunTask`
- Workspace execution mode: Remote (or Agent)
- Post-apply Run Task attached to the workspace
- A TFE API token (user or team token)

You do not need Terraform CLI for the API-driven approach; the scripts use `curl` and `tar`.

## Configure the TFE API token
Create a token in the UI:
- `https://ech-10-157-151-43.mastercard.int/app/settings/tokens`

Save it on the VM (or wherever you run the scripts) in:
`~/.terraform.d/credentials.tfrc.json`

```json
{
  "credentials": {
    "ech-10-157-151-43.mastercard.int": {
      "token": "<YOUR_TFE_TOKEN>"
    }
  }
}
```

## Trigger an API-driven run

Recommended (auto-queue):

```bash
./trigger-auto-queue.sh
```

Manual run creation (more control/debugging):

```bash
./trigger-run-api.sh
```

Both scripts:
- Create a configuration version
- Upload a tarball of this folder’s Terraform files
- Queue/create a run in workspace `kambiz/RunTask`

## Verify the Run Task
- In the TFE UI: org `kambiz` → workspace `RunTask` → Runs → open the run
- After Apply completes, check the **Run Tasks** section
- Confirm your Java service received the callback and triggered the intended Ansible playbook

## Notes
- `main.tf` intentionally forces changes every run using `timestamp()` in a `null_resource` trigger.
- The scripts currently use `curl -k` (skip TLS verification) to work around internal certificate constraints. For a production application, install/trust the internal CA or fix the TFE certificate to include SANs.
