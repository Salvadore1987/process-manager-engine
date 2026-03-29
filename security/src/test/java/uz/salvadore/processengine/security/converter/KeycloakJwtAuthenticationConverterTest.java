package uz.salvadore.processengine.security.converter;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter =
            new KeycloakJwtAuthenticationConverter("realm_access.roles");

    @Test
    void shouldExtractRolesFromRealmAccess() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "realm_access", Map.of("roles", List.of("process-admin", "process-operator"))
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PROCESS_ADMIN", "ROLE_PROCESS_OPERATOR");
    }

    @Test
    void shouldIgnoreUnknownRoles() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "realm_access", Map.of("roles", List.of("process-admin", "some-other-role", "offline_access"))
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PROCESS_ADMIN");
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNoRealmAccess() {
        // Arrange
        Jwt jwt = buildJwt(Map.of());

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNoRolesInRealmAccess() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "realm_access", Map.of("other_field", "value")
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenRolesListIsEmpty() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "realm_access", Map.of("roles", List.of())
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void shouldExtractAllFourRoles() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "realm_access", Map.of("roles", List.of(
                        "process-admin", "process-operator", "process-viewer", "process-deployer"))
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_PROCESS_ADMIN",
                        "ROLE_PROCESS_OPERATOR",
                        "ROLE_PROCESS_VIEWER",
                        "ROLE_PROCESS_DEPLOYER"
                );
    }

    @Test
    void shouldSetPreferredUsernameAsName() {
        // Arrange
        Jwt jwt = buildJwt(Map.of(
                "preferred_username", "testuser",
                "realm_access", Map.of("roles", List.of("process-viewer"))
        ));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getName()).isEqualTo("testuser");
    }

    private Jwt buildJwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "test-user-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));

        claims.forEach(builder::claim);

        return builder.build();
    }
}
