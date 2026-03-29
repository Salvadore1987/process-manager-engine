package uz.salvadore.processengine.security.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        // Arrange & Act
        SecurityProperties properties = new SecurityProperties();

        // Assert
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getIssuerUri()).isNull();
        assertThat(properties.getJwkSetUri()).isNull();
        assertThat(properties.getRoleClaimPath()).isEqualTo("realm_access.roles");
    }

    @Test
    void shouldAllowSettingValues() {
        // Arrange
        SecurityProperties properties = new SecurityProperties();

        // Act
        properties.setEnabled(false);
        properties.setIssuerUri("http://keycloak:8180/realms/test");
        properties.setJwkSetUri("http://keycloak:8180/realms/test/protocol/openid-connect/certs");
        properties.setRoleClaimPath("custom.path.roles");

        // Assert
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getIssuerUri()).isEqualTo("http://keycloak:8180/realms/test");
        assertThat(properties.getJwkSetUri()).isEqualTo("http://keycloak:8180/realms/test/protocol/openid-connect/certs");
        assertThat(properties.getRoleClaimPath()).isEqualTo("custom.path.roles");
    }
}
