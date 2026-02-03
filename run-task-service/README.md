# Terraform Run Task Service

Spring Boot service that receives Terraform Enterprise (TFE) Run Task webhooks and triggers Ansible playbooks via ansible-runner.

## Architecture

### Complete Flow (API-Driven Runs)

```
┌─────────────────┐
│   Application   │ (Triggers infrastructure changes)
└────────┬────────┘
         │ POST /api/v2/runs (with config version)
         ↓
┌─────────────────────────────────────────────────────────┐
│           Terraform Enterprise (TFE)                    │
│   Org: kambiz | Workspace: RunTask                      │
│  Host: TODO                                             │
│                                                         │
│  1. Queue Run                                           │
│  2. Execute Terraform Plan                              │
│  3. Execute Terraform Apply                             │
│  4. Trigger Post-Apply Run Task   ──────────────┐       │
└─────────────────────────────────────────────────┼───────┘
                                                  │
         POST https://your-endpoint/api/run-task  │
         (with run details + callback token)      │
                                                  ↓
┌──────────────────────────────────────────────────────────┐
│         Spring Boot Run Task Service (This App)          │
│                                                          │
│  1. Receive webhook from TFE                             │
│  2. Parse run details (run_id, workspace, stage)         │
│  3. Execute ansible-runner  ──────────────┐              │
│  4. Parse ansible results                 │              │
│  5. Return status to TFE                  │              │
└───────────────────────────────────────────┼──────────────┘
                                            │
                                            ↓
                                  ┌─────────────────────┐
                                  │   ansible-runner    │
                                  │                     │
                                  │  Execute playbook   │
                                  └─────────┬───────────┘
                                            │
                                            ↓
                                  ┌─────────────────────┐
                                  │  Ansible Playbook   │
                                  │                     │
                                  │  Infrastructure     │
                                  │  Configuration      │
                                  └─────────────────────┘
```

### Key Components

- **TFE Workspace**: `kambiz/RunTask` with post-apply Run Task configured
- **Run Task Stage**: `post_apply` (fires after terraform apply completes)
- **Enforcement**: Advisory (run continues even if task fails)
- **Java Service**: Receives TFE webhook, triggers Ansible, returns status
- **Ansible**: Actual infrastructure configuration/provisioning

## Prerequisites

- Java 17+
- Maven 3.6+
- Python 3.8+ with ansible-runner installed
- ansible-runner project configured

## Configuration

Edit `src/main/resources/application.properties`:

```properties
server.port=8080
ansible.runner.project.path=/home/dc-user/workspace/terraform-ansible-spike/runner-test
ansible.runner.playbook=playbooks/test.yml
ansible.runner.inventory=inventory/hosts
ansible.runner.python.path=python3.8
```

## Build and Run

```bash
# Build
mvn clean package

# Run
java -jar target/run-task-service-1.0.0.jar

# Or use Maven directly
mvn spring-boot:run
```

## API Endpoints

### Health Check
```bash
curl http://localhost:8080/api/health
```

### Run Task Webhook (simulated)
```bash
curl -X POST http://localhost:8080/api/run-task \
  -H "Content-Type: application/json" \
  -d '{
    "run_id": "run-test123",
    "workspace_name": "test-workspace",
    "organization_name": "test-org",
    "stage": "post_apply"
  }'
```

## Response Format

Success:
```json
{
  "status": "passed",
  "message": "Ansible playbook executed successfully",
  "url": "/path/to/artifacts/stdout"
}
```

Failure:
```json
{
  "status": "failed",
  "message": "Ansible playbook execution failed",
  "url": "/path/to/artifacts/stdout"
}
```

## Integration with Terraform Enterprise

### Setup Steps

1. **Ensure service is accessible from TFE**
   - Internal network: `http://your-host:8080/api/run-task`
   - Or use ngrok for testing: `https://xxxxx.ngrok.io/api/run-task`

2. **Register Run Task in TFE UI**
   - Navigate to: `Organization Settings → Run Tasks`
   - Click "Create a run task"
   - Name: `Ansible Provisioning Task`
   - Endpoint URL: `https://your-host:8080/api/run-task`
   - HMAC Key: (optional, for webhook verification)

3. **Attach Run Task to Workspace**
   - Go to workspace: `barkha/RunTask`
   - Settings → Run Tasks
   - Select "Ansible Provisioning Task"
   - Stage: `Post-apply`
   - Enforcement: `Advisory` (or `Mandatory` to block on failure)

4. **Trigger API-Driven Run**
   ```bash
   # Create configuration version and upload
   # See: runner-test/tfe-smoke/trigger-run-api.sh
   
   # Or use auto-queue script
   ./runner-test/tfe-smoke/trigger-auto-queue.sh
   ```

5. **Monitor Execution**
   - TFE UI: View run progress and Run Task results
   - Service logs: See Ansible execution output
   - Ansible artifacts: Check runner output directory

### Expected Webhook Payload from TFE

```json
{
  "payload_version": 1,
  "stage": "post_apply",
  "access_token": "...",
  "task_result_id": "taskrs-...",
  "task_result_enforcement_level": "advisory",
  "task_result_callback_url": "https://tfe-host/api/v2/task-results/...",
  "run_app_url": "https://tfe-host/app/org/workspace/runs/run-id",
  "run_id": "run-...",
  "run_message": "API-driven run",
  "run_created_at": "2026-01-29T...",
  "run_created_by": "admin",
  "workspace_id": "ws-...",
  "workspace_name": "RunTask",
  "workspace_app_url": "https://tfe-host/app/org/workspace",
  "organization_name": "barkha"
}
```

## Logs

Application logs show:
- Incoming webhook details
- ansible-runner execution output
- Final status determination
- Response sent to TFC
