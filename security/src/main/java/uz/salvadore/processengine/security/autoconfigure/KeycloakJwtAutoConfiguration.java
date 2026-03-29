package uz.salvadore.processengine.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.security.converter.KeycloakJwtAuthenticationConverter;

@AutoConfiguration
@ConditionalOnProperty(name = "process-engine.security.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakJwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter(
            SecurityProperties securityProperties) {
        return new KeycloakJwtAuthenticationConverter(securityProperties.getRoleClaimPath());
    }
}
