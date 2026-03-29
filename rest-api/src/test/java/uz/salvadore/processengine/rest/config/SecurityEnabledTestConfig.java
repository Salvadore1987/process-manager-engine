package uz.salvadore.processengine.rest.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import uz.salvadore.processengine.security.converter.KeycloakJwtAuthenticationConverter;

import static org.mockito.Mockito.mock;

@TestConfiguration
@EnableWebSecurity
public class SecurityEnabledTestConfig {

    private static final String ADMIN = "PROCESS_ADMIN";
    private static final String OPERATOR = "PROCESS_OPERATOR";
    private static final String VIEWER = "PROCESS_VIEWER";
    private static final String DEPLOYER = "PROCESS_DEPLOYER";

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter("realm_access.roles");
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/definitions").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/definitions/validate").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/definitions/{key}").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.GET, "/api/v1/definitions/**").hasAnyRole(ADMIN, OPERATOR, VIEWER, DEPLOYER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/instances").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/suspend").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/resume").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/instances/*").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/instances/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/variables").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/instances/*/variables/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/history/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/incidents/*/resolve").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter()))
                );
        return http.build();
    }
}
