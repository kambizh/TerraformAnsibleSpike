package com.terraform.runtask.controller;

import com.terraform.runtask.model.TerraformCallback;
import com.terraform.runtask.model.TerraformWebhook;
import com.terraform.runtask.service.AnsibleRunnerService;
import com.terraform.runtask.service.TerraformCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RunTaskController {

    private final AnsibleRunnerService ansibleRunnerService;
    private final TerraformCallbackService callbackService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Terraform Run Task Service");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run-task")
    public ResponseEntity<Map<String, String>> handleRunTask(@RequestBody TerraformWebhook webhook) {
        log.info("=== Received Run Task webhook ===");
        log.info("Run ID: {}", webhook.getRunId());
        log.info("Workspace: {}", webhook.getWorkspaceName());
        log.info("Organization: {}", webhook.getOrganizationName());
        log.info("Stage: {}", webhook.getStage());
        log.info("Task Result ID: {}", webhook.getTaskResultId());
        log.info("Callback URL: {}", webhook.getTaskResultCallbackUrl());
        log.info("Access Token: {}", webhook.getAccessToken() != null ? "***provided***" : "null");

        try {
            // Execute Ansible and send callback synchronously
            ansibleRunnerService.executePlaybookAndCallback(webhook);
            
            log.info(" Ansible execution completed and callback sent to TFE");

            // Return success (though TFE only cares about the callback)
            Map<String, String> response = new HashMap<>();
            response.put("status", "completed");
            response.put("message", "Ansible execution completed and callback sent to TFE");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Error executing Ansible or sending callback ===", e);
            
            // Try to send failure callback
            TerraformCallback callback = TerraformCallback.builder()
                    .status("failed")
                    .message("Failed to execute Ansible: " + e.getMessage())
                    .build();
            
            if (webhook.getTaskResultCallbackUrl() != null) {
                callbackService.sendCallback(
                    webhook.getTaskResultCallbackUrl(), 
                    callback, 
                    webhook.getAccessToken()
                );
            }

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/run-task/test")
    public ResponseEntity<Map<String, String>> testRunTask(
            @RequestParam(defaultValue = "test-run") String runId,
            @RequestParam(defaultValue = "test-workspace") String workspaceName,
            @RequestParam(defaultValue = "test-org") String organizationName) {
        
        log.info("Received GET test request: run_id={}, workspace={}", runId, workspaceName);

        // Build a test webhook
        TerraformWebhook webhook = TerraformWebhook.builder()
                .runId(runId)
                .workspaceName(workspaceName)
                .organizationName(organizationName)
                .stage("post_apply")
                .payloadVersion(1)
                .build();

        return handleRunTask(webhook);
    }
}