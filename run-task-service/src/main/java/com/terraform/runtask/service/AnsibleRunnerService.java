package com.terraform.runtask.service;

import com.terraform.runtask.model.TerraformWebhook;
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
public class AnsibleRunnerService {

    @Value("${ansible.runner.project.path}")
    private String projectPath;

    @Value("${ansible.runner.playbook}")
    private String playbook;

    @Value("${ansible.runner.inventory}")
    private String inventory;

    @Value("${ansible.runner.python.path:python3.8}")
    private String pythonPath;

    public boolean executePlaybook(TerraformWebhook webhook) {
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