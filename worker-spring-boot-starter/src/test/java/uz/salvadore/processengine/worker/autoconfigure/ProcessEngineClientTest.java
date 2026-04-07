package uz.salvadore.processengine.worker.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ProcessEngineClient}.
 *
 * <p>Strategy: ProcessEngineClient uses {@code java.net.http.HttpClient} internally (not injectable),
 * so we test it with a lightweight embedded HTTP server ({@code com.sun.net.httpserver.HttpServer}).
 * This avoids the complexity of mocking sealed JDK HTTP classes while still verifying real HTTP
 * behavior — multipart requests, status code handling, and authentication flow.
 */
class ProcessEngineClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final byte[] SAMPLE_BPMN = "<bpmn/>".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private WorkerProperties.AuthProperties disabledAuth() {
        WorkerProperties.AuthProperties auth = new WorkerProperties.AuthProperties();
        auth.setEnabled(false);
        return auth;
    }

    private WorkerProperties.AuthProperties enabledAuth(String tokenUri) {
        WorkerProperties.AuthProperties auth = new WorkerProperties.AuthProperties();
        auth.setEnabled(true);
        auth.setTokenUri(tokenUri);
        auth.setClientId("test-client");
        auth.setClientSecret("test-secret");
        auth.setGrantType("client_credentials");
        return auth;
    }

    // ---- Test groups ----

    @Nested
    @DisplayName("deployDefinition — single file deployment")
    class DeployDefinitionTests {

        @Test
        @DisplayName("sends multipart POST to /api/v1/definitions and succeeds on 201")
        void deployDefinition_succeeds_onHttp201() {
            // Arrange
            AtomicReference<String> capturedContentType = new AtomicReference<>();
            AtomicReference<String> capturedBody = new AtomicReference<>();

            server.createContext("/api/v1/definitions", exchange -> {
                capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl, disabledAuth(), OBJECT_MAPPER);

            // Act
            client.deployDefinition("test.bpmn", SAMPLE_BPMN);

            // Assert
            assertThat(capturedContentType.get()).startsWith("multipart/form-data; boundary=");
            assertThat(capturedBody.get())
                    .contains("filename=\"test.bpmn\"")
                    .contains("<bpmn/>");
        }

        @Test
        @DisplayName("throws DeploymentException on non-201 response")
        void deployDefinition_throws_onNon201Response() {
            // Arrange
            server.createContext("/api/v1/definitions", exchange -> {
                byte[] response = "Bad Request".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl, disabledAuth(), OBJECT_MAPPER);

            // Act & Assert
            assertThatThrownBy(() -> client.deployDefinition("test.bpmn", SAMPLE_BPMN))
                    .isInstanceOf(ProcessEngineClient.DeploymentException.class)
                    .hasMessageContaining("Failed to deploy 'test.bpmn'")
                    .hasMessageContaining("HTTP 400");
        }

        @Test
        @DisplayName("throws DeploymentException on connection failure")
        void deployDefinition_throws_onConnectionFailure() {
            // Arrange — server not started, port is bound but nothing listens
            server.stop(0);
            ProcessEngineClient client = new ProcessEngineClient(
                    "http://localhost:1", disabledAuth(), OBJECT_MAPPER);

            // Act & Assert
            assertThatThrownBy(() -> client.deployDefinition("test.bpmn", SAMPLE_BPMN))
                    .isInstanceOf(ProcessEngineClient.DeploymentException.class)
                    .hasMessageContaining("HTTP request failed");
        }
    }

    @Nested
    @DisplayName("deployBundle — multiple file deployment")
    class DeployBundleTests {

        @Test
        @DisplayName("sends multipart POST to /api/v1/definitions/bundle and succeeds on 201")
        void deployBundle_succeeds_onHttp201() {
            // Arrange
            AtomicReference<String> capturedBody = new AtomicReference<>();

            server.createContext("/api/v1/definitions/bundle", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl, disabledAuth(), OBJECT_MAPPER);
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("main.bpmn", "<main/>".getBytes(StandardCharsets.UTF_8));
            files.put("sub.bpmn", "<sub/>".getBytes(StandardCharsets.UTF_8));

            // Act
            client.deployBundle(files);

            // Assert
            assertThat(capturedBody.get())
                    .contains("filename=\"main.bpmn\"")
                    .contains("filename=\"sub.bpmn\"")
                    .contains("<main/>")
                    .contains("<sub/>");
        }

        @Test
        @DisplayName("throws DeploymentException on non-201 response")
        void deployBundle_throws_onNon201Response() {
            // Arrange
            server.createContext("/api/v1/definitions/bundle", exchange -> {
                byte[] response = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl, disabledAuth(), OBJECT_MAPPER);
            Map<String, byte[]> files = Map.of("a.bpmn", SAMPLE_BPMN, "b.bpmn", SAMPLE_BPMN);

            // Act & Assert
            assertThatThrownBy(() -> client.deployBundle(files))
                    .isInstanceOf(ProcessEngineClient.DeploymentException.class)
                    .hasMessageContaining("Failed to deploy bundle")
                    .hasMessageContaining("HTTP 500");
        }
    }

    @Nested
    @DisplayName("Authentication")
    class AuthenticationTests {

        @Test
        @DisplayName("fetches access token and includes Bearer header when auth is enabled")
        void deployDefinition_includesBearerToken_whenAuthEnabled() {
            // Arrange
            AtomicReference<String> capturedAuthHeader = new AtomicReference<>();

            String tokenEndpointPath = "/realms/test/protocol/openid-connect/token";
            server.createContext(tokenEndpointPath, exchange -> {
                String tokenResponse = "{\"access_token\":\"test-jwt-token\",\"expires_in\":300}";
                byte[] responseBytes = tokenResponse.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });
            server.createContext("/api/v1/definitions", exchange -> {
                capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            WorkerProperties.AuthProperties auth = enabledAuth(baseUrl + tokenEndpointPath);
            ProcessEngineClient client = new ProcessEngineClient(baseUrl, auth, OBJECT_MAPPER);

            // Act
            client.deployDefinition("test.bpmn", SAMPLE_BPMN);

            // Assert
            assertThat(capturedAuthHeader.get()).isEqualTo("Bearer test-jwt-token");
        }

        @Test
        @DisplayName("does not include Authorization header when auth is disabled")
        void deployDefinition_noAuthHeader_whenAuthDisabled() {
            // Arrange
            AtomicReference<String> capturedAuthHeader = new AtomicReference<>();

            server.createContext("/api/v1/definitions", exchange -> {
                capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl, disabledAuth(), OBJECT_MAPPER);

            // Act
            client.deployDefinition("test.bpmn", SAMPLE_BPMN);

            // Assert
            assertThat(capturedAuthHeader.get()).isNull();
        }

        @Test
        @DisplayName("throws DeploymentException when token endpoint returns non-200")
        void deployDefinition_throws_whenTokenEndpointFails() {
            // Arrange
            String tokenEndpointPath = "/realms/test/protocol/openid-connect/token";
            server.createContext(tokenEndpointPath, exchange -> {
                byte[] response = "Unauthorized".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            server.start();

            WorkerProperties.AuthProperties auth = enabledAuth(baseUrl + tokenEndpointPath);
            ProcessEngineClient client = new ProcessEngineClient(baseUrl, auth, OBJECT_MAPPER);

            // Act & Assert
            assertThatThrownBy(() -> client.deployDefinition("test.bpmn", SAMPLE_BPMN))
                    .isInstanceOf(ProcessEngineClient.DeploymentException.class)
                    .hasMessageContaining("Failed to obtain access token");
        }

        @Test
        @DisplayName("caches token and reuses it for subsequent requests")
        void deployDefinition_cachesToken_acrossCalls() {
            // Arrange
            int[] tokenRequestCount = {0};

            String tokenEndpointPath = "/realms/test/protocol/openid-connect/token";
            server.createContext(tokenEndpointPath, exchange -> {
                tokenRequestCount[0]++;
                String tokenResponse = "{\"access_token\":\"cached-token\",\"expires_in\":300}";
                byte[] responseBytes = tokenResponse.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });
            server.createContext("/api/v1/definitions", exchange -> {
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            WorkerProperties.AuthProperties auth = enabledAuth(baseUrl + tokenEndpointPath);
            ProcessEngineClient client = new ProcessEngineClient(baseUrl, auth, OBJECT_MAPPER);

            // Act — deploy twice
            client.deployDefinition("first.bpmn", SAMPLE_BPMN);
            client.deployDefinition("second.bpmn", SAMPLE_BPMN);

            // Assert — token endpoint called only once
            assertThat(tokenRequestCount[0]).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("URL normalization")
    class UrlNormalizationTests {

        @Test
        @DisplayName("strips trailing slash from engine URL to avoid double slashes")
        void constructor_stripsTrailingSlash() {
            // Arrange
            AtomicReference<String> capturedUri = new AtomicReference<>();

            server.createContext("/api/v1/definitions", exchange -> {
                capturedUri.set(exchange.getRequestURI().toString());
                exchange.sendResponseHeaders(201, 0);
                exchange.close();
            });
            server.start();

            ProcessEngineClient client = new ProcessEngineClient(baseUrl + "/", disabledAuth(), OBJECT_MAPPER);

            // Act
            client.deployDefinition("test.bpmn", SAMPLE_BPMN);

            // Assert — no double slash in the path
            assertThat(capturedUri.get()).isEqualTo("/api/v1/definitions");
        }
    }
}
