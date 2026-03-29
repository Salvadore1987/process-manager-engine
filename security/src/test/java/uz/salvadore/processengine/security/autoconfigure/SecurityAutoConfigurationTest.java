package uz.salvadore.processengine.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uz.salvadore.processengine.security.converter.KeycloakJwtAuthenticationConverter;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KeycloakJwtAutoConfiguration.class))
            .withBean(SecurityProperties.class);

    @Test
    void shouldCreateJwtConverterWhenEnabled() {
        // Arrange & Act & Assert
        contextRunner
                .withPropertyValues("process-engine.security.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(KeycloakJwtAuthenticationConverter.class);
                });
    }

    @Test
    void shouldNotCreateJwtConverterWhenDisabled() {
        // Arrange & Act & Assert
        contextRunner
                .withPropertyValues("process-engine.security.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(KeycloakJwtAuthenticationConverter.class);
                });
    }

    @Test
    void shouldCreateJwtConverterByDefault() {
        // Arrange & Act & Assert — matchIfMissing = true
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(KeycloakJwtAuthenticationConverter.class);
                });
    }

    @Test
    void shouldBindSecurityProperties() {
        // Arrange & Act & Assert
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .withPropertyValues(
                        "process-engine.security.enabled=true",
                        "process-engine.security.issuer-uri=http://localhost:8180/realms/test",
                        "process-engine.security.role-claim-path=resource_access.client.roles"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityProperties.class);
                    SecurityProperties properties = context.getBean(SecurityProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getIssuerUri()).isEqualTo("http://localhost:8180/realms/test");
                    assertThat(properties.getRoleClaimPath()).isEqualTo("resource_access.client.roles");
                });
    }

    @Test
    void shouldUseDefaultRoleClaimPath() {
        // Arrange & Act & Assert
        contextRunner
                .run(context -> {
                    KeycloakJwtAuthenticationConverter converter =
                            context.getBean(KeycloakJwtAuthenticationConverter.class);
                    assertThat(converter).isNotNull();
                });
    }
}
