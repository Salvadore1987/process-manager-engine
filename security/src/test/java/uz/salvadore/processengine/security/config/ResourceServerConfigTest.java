package uz.salvadore.processengine.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.security.autoconfigure.KeycloakJwtAutoConfiguration;
import uz.salvadore.processengine.security.autoconfigure.SecurityAutoConfiguration;
import uz.salvadore.processengine.security.autoconfigure.SecurityWebAutoConfiguration;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        WebMvcAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        KeycloakJwtAutoConfiguration.class,
        SecurityWebAutoConfiguration.class,
        ResourceServerConfigTest.TestConfig.class
})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "process-engine.security.enabled=true"
})
class ResourceServerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Actuator: public ---

    @Test
    void shouldAllowActuatorWithoutAuth() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // --- Definitions ---

    @Test
    void shouldAllowAdminToDeployDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowDeployerToDeployDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_DEPLOYER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToDeployDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUnauthenticatedDeployDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/definitions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowViewerToListDefinitions() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminToDeleteDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(delete("/api/v1/definitions/order-process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyOperatorToDeleteDefinition() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(delete("/api/v1/definitions/order-process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    // --- Instances ---

    @Test
    void shouldAllowOperatorToStartInstance() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/instances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToStartInstance() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/instances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowViewerToGetInstance() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/instances/123")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToSuspendInstance() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(put("/api/v1/instances/123/suspend")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    // --- Messages ---

    @Test
    void shouldAllowOperatorToSendMessage() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToSendMessage() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // --- History ---

    @Test
    void shouldAllowViewerToAccessHistory() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/123/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyDeployerToAccessHistory() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/123/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_DEPLOYER"))))
                .andExpect(status().isForbidden());
    }

    // --- Incidents ---

    @Test
    void shouldAllowViewerToListIncidents() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/incidents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToResolveIncident() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(put("/api/v1/incidents/123/resolve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToResolveIncident() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(put("/api/v1/incidents/123/resolve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // --- Unauthenticated ---

    @Test
    void shouldDenyUnauthenticatedAccessToInstances() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/instances/123"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    static class TestConfig {

        @Bean
        public JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        public TestDefinitionController testDefinitionController() {
            return new TestDefinitionController();
        }

        @Bean
        public TestInstanceController testInstanceController() {
            return new TestInstanceController();
        }

        @Bean
        public TestMessageController testMessageController() {
            return new TestMessageController();
        }

        @Bean
        public TestHistoryController testHistoryController() {
            return new TestHistoryController();
        }

        @Bean
        public TestIncidentController testIncidentController() {
            return new TestIncidentController();
        }

        @Bean
        public TestActuatorController testActuatorController() {
            return new TestActuatorController();
        }
    }

    @RestController
    static class TestDefinitionController {
        @PostMapping("/api/v1/definitions")
        public String deploy() { return "ok"; }

        @GetMapping("/api/v1/definitions")
        public String list() { return "ok"; }

        @GetMapping("/api/v1/definitions/{key}")
        public String getByKey(@PathVariable("key") String key) { return "ok"; }

        @DeleteMapping("/api/v1/definitions/{key}")
        public String undeploy(@PathVariable("key") String key) { return "ok"; }
    }

    @RestController
    static class TestInstanceController {
        @PostMapping("/api/v1/instances")
        public String start() { return "ok"; }

        @GetMapping("/api/v1/instances/{id}")
        public String getById(@PathVariable("id") String id) { return "ok"; }

        @PutMapping("/api/v1/instances/{id}/suspend")
        public String suspend(@PathVariable("id") String id) { return "ok"; }

        @PutMapping("/api/v1/instances/{id}/resume")
        public String resume(@PathVariable("id") String id) { return "ok"; }

        @DeleteMapping("/api/v1/instances/{id}")
        public String terminate(@PathVariable("id") String id) { return "ok"; }
    }

    @RestController
    static class TestMessageController {
        @PostMapping("/api/v1/messages")
        public String send() { return "ok"; }
    }

    @RestController
    static class TestHistoryController {
        @GetMapping("/api/v1/history/instances/{id}/events")
        public String events(@PathVariable("id") String id) { return "ok"; }
    }

    @RestController
    static class TestIncidentController {
        @GetMapping("/api/v1/incidents")
        public String list() { return "ok"; }

        @GetMapping("/api/v1/incidents/{id}")
        public String getById(@PathVariable("id") String id) { return "ok"; }

        @PutMapping("/api/v1/incidents/{id}/resolve")
        public String resolve(@PathVariable("id") String id) { return "ok"; }
    }

    @RestController
    static class TestActuatorController {
        @GetMapping("/actuator/health")
        public String health() { return "ok"; }
    }
}
