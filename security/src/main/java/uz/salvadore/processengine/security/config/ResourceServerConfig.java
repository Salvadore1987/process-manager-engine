package uz.salvadore.processengine.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import uz.salvadore.processengine.security.converter.KeycloakJwtAuthenticationConverter;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "process-engine.security.enabled", havingValue = "true", matchIfMissing = true)
public class ResourceServerConfig {

    private static final String ADMIN = "PROCESS_ADMIN";
    private static final String OPERATOR = "PROCESS_OPERATOR";
    private static final String VIEWER = "PROCESS_VIEWER";
    private static final String DEPLOYER = "PROCESS_DEPLOYER";

    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    public ResourceServerConfig(KeycloakJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator — public
                        .requestMatchers("/actuator/**").permitAll()

                        // Definitions — deploy/validate/undeploy
                        .requestMatchers(HttpMethod.POST, "/api/v1/definitions").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/definitions/validate").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/definitions/{key}").hasAnyRole(ADMIN, DEPLOYER)
                        .requestMatchers(HttpMethod.GET, "/api/v1/definitions/**").hasAnyRole(ADMIN, OPERATOR, VIEWER, DEPLOYER)

                        // Instances — write operations
                        .requestMatchers(HttpMethod.POST, "/api/v1/instances").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/suspend").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/resume").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/instances/*").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/instances/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)

                        // Variables — write
                        .requestMatchers(HttpMethod.PUT, "/api/v1/instances/*/variables").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/instances/*/variables/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)

                        // Messages
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages").hasAnyRole(ADMIN, OPERATOR)

                        // History — read only
                        .requestMatchers(HttpMethod.GET, "/api/v1/history/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)

                        // Incidents
                        .requestMatchers(HttpMethod.PUT, "/api/v1/incidents/*/resolve").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/**").hasAnyRole(ADMIN, OPERATOR, VIEWER)

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }
}
