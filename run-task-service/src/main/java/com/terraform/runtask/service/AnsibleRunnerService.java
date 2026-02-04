package com.terraform.runtask.service;

import com.terraform.runtask.model.TerraformCallback;
import com.terraform.runtask.model.TerraformWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnsibleRunnerService {

    private final TerraformCallbackService callbackService;

    @Value("${ansible.runner.project.path}")
    private String projectPath;

    @Value("${ansible.runner.playbook}")
    private String playbook;

    @Value("${ansible.runner.inventory}")
    private String inventory;

    @Value("${ansible.runner.python.path:python3.8}")
    private String pythonPath;

    public void executePlaybookAndCallback(TerraformWebhook webhook) {
        long startTime = System.currentTimeMillis();
        
        // Execute Ansible synchronously
        boolean success = executePlaybook(webhook);
        
        long executionTime = System.currentTimeMillis() - startTime;
        log.info("Ansible execution completed in {}ms with status: {}", 
                 executionTime, success ? "SUCCESS" : "FAILED");
        
        // Send callback to TFE with actual result
        sendCallbackToTFE(webhook, success, executionTime);
    }

    private void sendCallbackToTFE(TerraformWebhook webhook, boolean success, long executionTime) {
        if (webhook.getTaskResultCallbackUrl() == null || webhook.getTaskResultCallbackUrl().isEmpty()) {
            log.warn("⚠️ No callback URL provided - cannot report result to TFE!");
            return;
        }

        TerraformCallback callback = TerraformCallback.builder()
                .status(success ? "passed" : "failed")
                .message(success ? 
                    String.format("Ansible playbook executed successfully (run_id: %s, execution_time: %dms)", 
                        webhook.getRunId(), executionTime) : 
                    String.format("Ansible playbook execution failed (run_id: %s)", webhook.getRunId()))
                .url(getArtifactsUrl())
                .build();

        callbackService.sendCallback(
            webhook.getTaskResultCallbackUrl(), 
            callback, 
            webhook.getAccessToken()
        );
    }

    private boolean executePlaybook(TerraformWebhook webhook) {
        try {
            log.info("Executing ansible-runner for run: {}", webhook.getRunId());

            ProcessBuilder pb = new ProcessBuilder(
                pythonPath, "-m", "ansible_runner", "run",
                projectPath,
                "-p", playbook,
                "-i", inventory
            );

            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("ansible-runner: {}", line);
                }
            }

            int exitCode = process.waitFor();
            log.info("ansible-runner completed with exit code: {}", exitCode);

            return checkStatus();

        } catch (Exception e) {
            log.error("Failed to execute ansible-runner", e);
            return false;
        }
    }

    private boolean checkStatus() {
        try {
            Path artifactsPath = Paths.get(projectPath, "artifacts");
            
            Path statusFile = Paths.get(projectPath, "artifacts/inventory/hosts/status");
            
            if (Files.exists(statusFile)) {
                String status = Files.readString(statusFile).trim();
                log.info("Ansible run status: {}", status);
                return "successful".equals(status);
            }
            
            log.warn("Status file not found");
            return false;
            
        } catch (Exception e) {
            log.error("Error reading status file", e);
            return false;
        }
    }

    public String getArtifactsUrl() {
        return projectPath + "/artifacts/inventory/hosts/stdout";
    }
}