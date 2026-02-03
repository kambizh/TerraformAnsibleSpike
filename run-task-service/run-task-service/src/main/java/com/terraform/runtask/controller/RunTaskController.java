package com.terraform.runtask.controller;

import com.terraform.runtask.model.TerraformCallback;
import com.terraform.runtask.model.TerraformWebhook;
import com.terraform.runtask.service.AnsibleRunnerService;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Terraform Run Task Service");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run-task")
    public ResponseEntity<TerraformCallback> handleRunTask(@RequestBody TerraformWebhook webhook) {
        log.info("Received Run Task webhook: run_id={}, workspace={}", 
                 webhook.getRunId(), webhook.getWorkspaceName());

        try {
            // Execute Ansible playbook
            boolean success = ansibleRunnerService.executePlaybook(webhook);

            // Build TFC response
            TerraformCallback callback = TerraformCallback.builder()
                    .status(success ? "passed" : "failed")
                    .message(success ? "Ansible playbook executed successfully" : "Ansible playbook execution failed")
                    .url(ansibleRunnerService.getArtifactsUrl())
                    .build();

            log.info("Returning status: {}", callback.getStatus());
            return ResponseEntity.ok(callback);

        } catch (Exception e) {
            log.error("Error executing playbook", e);
            TerraformCallback callback = TerraformCallback.builder()
                    .status("failed")
                    .message("Error: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(callback);
        }
    }
}