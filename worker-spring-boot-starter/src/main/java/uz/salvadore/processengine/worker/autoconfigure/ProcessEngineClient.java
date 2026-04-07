package uz.salvadore.processengine.worker.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ProcessEngineClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessEngineClient.class);

    private final String engineUrl;
    private final WorkerProperties.AuthProperties authProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public ProcessEngineClient(String engineUrl,
                               WorkerProperties.AuthProperties authProperties,
                               ObjectMapper objectMapper) {
        this.engineUrl = engineUrl.endsWith("/") ? engineUrl.substring(0, engineUrl.length() - 1) : engineUrl;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void deployDefinition(String filename, byte[] bpmnContent) {
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartSingleFile(boundary, "file", filename, bpmnContent);

        HttpRequest request = buildRequest("/api/v1/definitions", boundary, body);

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 201) {
            throw new DeploymentException(
                    "Failed to deploy '" + filename + "': HTTP " + response.statusCode() + " — " + response.body());
        }

        log.info("Deployed definition: {} (HTTP {})", filename, response.statusCode());
    }

    public void deployBundle(Map<String, byte[]> files) {
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartMultipleFiles(boundary, "files", files);

        HttpRequest request = buildRequest("/api/v1/definitions/bundle", boundary, body);

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 201) {
            throw new DeploymentException(
                    "Failed to deploy bundle (" + files.size() + " files): HTTP "
                            + response.statusCode() + " — " + response.body());
        }

        log.info("Deployed bundle: {} file(s) (HTTP {})", files.size(), response.statusCode());
    }

    private HttpRequest buildRequest(String path, String boundary, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(engineUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        if (authProperties.isEnabled()) {
            String token = getAccessToken();
            builder.header("Authorization", "Bearer " + token);
        }

        return builder.build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DeploymentException("HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException("HTTP request interrupted", e);
        }
    }

    private String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        String requestBody = "grant_type=" + URLEncoder.encode(authProperties.getGrantType(), StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(authProperties.getClientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(authProperties.getClientSecret(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authProperties.getTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new DeploymentException("Failed to obtain access token: HTTP " + response.statusCode());
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            cachedToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 30);
            return cachedToken;
        } catch (Exception e) {
            throw new DeploymentException("Failed to parse token response", e);
        }
    }

    private byte[] buildMultipartSingleFile(String boundary, String fieldName,
                                            String filename, byte[] content) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(fieldName)
                .append("\"; filename=\"").append(filename).append("\"\r\n");
        sb.append("Content-Type: application/xml\r\n\r\n");

        byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[header.length + content.length + footer.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(content, 0, result, header.length, content.length);
        System.arraycopy(footer, 0, result, header.length + content.length, footer.length);
        return result;
    }

    private byte[] buildMultipartMultipleFiles(String boundary, String fieldName,
                                               Map<String, byte[]> files) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(fieldName)
                    .append("\"; filename=\"").append(entry.getKey()).append("\"\r\n");
            sb.append("Content-Type: application/xml\r\n\r\n");
            sb.append(new String(entry.getValue(), StandardCharsets.UTF_8));
            sb.append("\r\n");
        }

        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static class DeploymentException extends RuntimeException {

        public DeploymentException(String message) {
            super(message);
        }

        public DeploymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
