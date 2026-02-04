package com.terraform.runtask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.runtask.model.TerraformCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerraformCallbackService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TerraformCallbackService() {
        // Use Apache HttpClient to support PATCH method
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    public void sendCallback(String callbackUrl, TerraformCallback callback, String accessToken) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("No callback URL provided, skipping callback");
            return;
        }

        try {
            log.info("╔═══════════════════════════════════════════════════════════");
            log.info("║ SENDING CALLBACK TO TFE");
            log.info("╠═══════════════════════════════════════════════════════════");
            log.info("║ Callback URL: {}", callbackUrl);
            log.info("║ Payload: {}", objectMapper.writeValueAsString(callback));
            log.info("╚═══════════════════════════════════════════════════════════");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.api+json"));
            headers.setAccept(java.util.List.of(MediaType.parseMediaType("application/vnd.api+json")));
            
            // Add authorization if token is provided
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.setBearerAuth(accessToken);
                log.info("Using Bearer token for callback authentication");
            }

            // Build JSON:API payload: { data: { type: "task-results", attributes: { ... } } }
            java.util.Map<String, Object> attributes = new java.util.HashMap<>();
            attributes.put("status", callback.getStatus());
            attributes.put("message", callback.getMessage());
            attributes.put("url", callback.getUrl());

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("type", "task-results");
            data.put("attributes", attributes);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("data", data);

            log.info("Payload (JSON:API): {}", objectMapper.writeValueAsString(payload));

            HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(payload, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    callbackUrl,
                    HttpMethod.PATCH,
                    request,
                    String.class
            );
            long duration = System.currentTimeMillis() - startTime;

            log.info("╔═══════════════════════════════════════════════════════════");
            log.info("║ CALLBACK RESPONSE FROM TFE (duration: {}ms)", duration);
            log.info("╠═══════════════════════════════════════════════════════════");
            log.info("║ Status Code: {}", response.getStatusCode());
            log.info("║ Response Body: {}", response.getBody());
            log.info("╚═══════════════════════════════════════════════════════════");

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Callback to TFE successful");
            } else {
                log.warn("Callback returned non-2xx status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════");
            log.error("║   CALLBACK TO TFE FAILED");
            log.error("╠═══════════════════════════════════════════════════════════");
            log.error("║ Callback URL: {}", callbackUrl);
            log.error("║ Error: {}", e.getMessage());
            log.error("╚═══════════════════════════════════════════════════════════", e);
        }
    }
}