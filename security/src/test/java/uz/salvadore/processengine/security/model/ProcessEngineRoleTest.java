package uz.salvadore.processengine.security.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessEngineRoleTest {

    @Test
    void shouldMapKeycloakRoleToEnum() {
        // Arrange & Act & Assert
        assertThat(ProcessEngineRole.fromKeycloakRole("process-admin")).isEqualTo(ProcessEngineRole.PROCESS_ADMIN);
        assertThat(ProcessEngineRole.fromKeycloakRole("process-operator")).isEqualTo(ProcessEngineRole.PROCESS_OPERATOR);
        assertThat(ProcessEngineRole.fromKeycloakRole("process-viewer")).isEqualTo(ProcessEngineRole.PROCESS_VIEWER);
        assertThat(ProcessEngineRole.fromKeycloakRole("process-deployer")).isEqualTo(ProcessEngineRole.PROCESS_DEPLOYER);
    }

    @Test
    void shouldReturnCorrectAuthority() {
        // Arrange & Act & Assert
        assertThat(ProcessEngineRole.PROCESS_ADMIN.toAuthority()).isEqualTo("ROLE_PROCESS_ADMIN");
        assertThat(ProcessEngineRole.PROCESS_OPERATOR.toAuthority()).isEqualTo("ROLE_PROCESS_OPERATOR");
        assertThat(ProcessEngineRole.PROCESS_VIEWER.toAuthority()).isEqualTo("ROLE_PROCESS_VIEWER");
        assertThat(ProcessEngineRole.PROCESS_DEPLOYER.toAuthority()).isEqualTo("ROLE_PROCESS_DEPLOYER");
    }

    @Test
    void shouldReturnCorrectKeycloakRole() {
        // Arrange & Act & Assert
        assertThat(ProcessEngineRole.PROCESS_ADMIN.getKeycloakRole()).isEqualTo("process-admin");
        assertThat(ProcessEngineRole.PROCESS_OPERATOR.getKeycloakRole()).isEqualTo("process-operator");
        assertThat(ProcessEngineRole.PROCESS_VIEWER.getKeycloakRole()).isEqualTo("process-viewer");
        assertThat(ProcessEngineRole.PROCESS_DEPLOYER.getKeycloakRole()).isEqualTo("process-deployer");
    }

    @Test
    void shouldThrowForUnknownKeycloakRole() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> ProcessEngineRole.fromKeycloakRole("unknown-role"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Keycloak role: unknown-role");
    }

    @Test
    void shouldCheckIfKeycloakRoleIsKnown() {
        // Arrange & Act & Assert
        assertThat(ProcessEngineRole.isKnownKeycloakRole("process-admin")).isTrue();
        assertThat(ProcessEngineRole.isKnownKeycloakRole("process-operator")).isTrue();
        assertThat(ProcessEngineRole.isKnownKeycloakRole("unknown")).isFalse();
        assertThat(ProcessEngineRole.isKnownKeycloakRole("")).isFalse();
    }

    @Test
    void shouldHaveFourRoles() {
        // Arrange & Act & Assert
        assertThat(ProcessEngineRole.values()).hasSize(4);
    }
}
