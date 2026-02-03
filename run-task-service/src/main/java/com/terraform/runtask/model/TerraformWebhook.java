package com.terraform.runtask.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerraformWebhook {
    
    @JsonProperty("run_id")
    private String runId;
    
    @JsonProperty("run_message")
    private String runMessage;
    
    @JsonProperty("run_created_at")
    private String runCreatedAt;
    
    @JsonProperty("run_created_by")
    private String runCreatedBy;
    
    @JsonProperty("workspace_id")
    private String workspaceId;
    
    @JsonProperty("workspace_name")
    private String workspaceName;
    
    @JsonProperty("organization_name")
    private String organizationName;
    
    @JsonProperty("payload_version")
    private Integer payloadVersion;
    
    @JsonProperty("stage")
    private String stage;
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("task_result_id")
    private String taskResultId;
    
    @JsonProperty("task_result_enforcement_level")
    private String taskResultEnforcementLevel;
    
    @JsonProperty("task_result_callback_url")
    private String taskResultCallbackUrl;
}
