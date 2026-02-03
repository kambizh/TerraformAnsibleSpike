package com.terraform.runtask.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerraformCallback {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("data")
    private CallbackData data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallbackData {
        @Builder.Default
        private String type = "task-results";
        
        @JsonProperty("attributes")
        private Attributes attributes;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Attributes {
            private String status;
            private String message;
            private String url;
        }
    }
}