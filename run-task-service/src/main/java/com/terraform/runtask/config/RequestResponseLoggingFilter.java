package com.terraform.runtask.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class RequestResponseLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Only log /api/run-task endpoints
        if (!httpRequest.getRequestURI().contains("/api/run-task")) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            logRequest(requestWrapper, duration);
            logResponse(responseWrapper, duration);

            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, long duration) {
        try {
            log.info("╔═══════════════════════════════════════════════════════════");
            log.info("║ INCOMING REQUEST FROM TFE");
            log.info("╠═══════════════════════════════════════════════════════════");
            log.info("║ Method: {} {}", request.getMethod(), request.getRequestURI());
            log.info("║ Remote Address: {}", request.getRemoteAddr());
            log.info("║ Headers:");
            
            var headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                log.info("║   {}: {}", headerName, request.getHeader(headerName));
            }
            
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content, StandardCharsets.UTF_8);
                log.info("║ Body:");
                log.info("║ {}", body);
            }
            log.info("╚═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            log.error("Error logging request", e);
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        try {
            log.info("╔═══════════════════════════════════════════════════════════");
            log.info("║ OUTGOING RESPONSE TO TFE (duration: {}ms)", duration);
            log.info("╠═══════════════════════════════════════════════════════════");
            log.info("║ Status: {}", response.getStatus());
            log.info("║ Headers:");
            
            for (String headerName : response.getHeaderNames()) {
                log.info("║   {}: {}", headerName, response.getHeader(headerName));
            }
            
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content, StandardCharsets.UTF_8);
                log.info("║ Body:");
                log.info("║ {}", body);
            }
            log.info("╚═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            log.error("Error logging response", e);
        }
    }
}