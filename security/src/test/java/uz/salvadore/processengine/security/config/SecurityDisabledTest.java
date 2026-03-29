package uz.salvadore.processengine.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.security.autoconfigure.SecurityAutoConfiguration;
import uz.salvadore.processengine.security.autoconfigure.SecurityWebAutoConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        WebMvcAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SecurityWebAutoConfiguration.class,
        SecurityDisabledTest.TestConfig.class
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "process-engine.security.enabled=false")
class SecurityDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowUnauthenticatedAccessWhenSecurityDisabled() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/api/v1/definitions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void shouldAllowAccessToActuatorWhenSecurityDisabled() throws Exception {
        // Arrange & Act & Assert
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Configuration
    static class TestConfig {

        @Bean
        public TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/definitions")
        public String definitions() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        public String health() {
            return "ok";
        }
    }
}
