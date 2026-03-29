package uz.salvadore.processengine.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import uz.salvadore.processengine.security.config.ResourceServerConfig;
import uz.salvadore.processengine.security.filter.SecurityDisabledFilter;

@AutoConfiguration(after = {SecurityAutoConfiguration.class, KeycloakJwtAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ResourceServerConfig.class, SecurityDisabledFilter.class})
public class SecurityWebAutoConfiguration {
}
