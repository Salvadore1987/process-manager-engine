package uz.salvadore.processengine.security.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uz.salvadore.processengine.security.model.ProcessEngineRole;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String roleClaimPath;

    public KeycloakJwtAuthenticationConverter(String roleClaimPath) {
        this.roleClaimPath = roleClaimPath;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = extractRoles(jwt);
        return roles.stream()
                .filter(ProcessEngineRole::isKnownKeycloakRole)
                .map(ProcessEngineRole::fromKeycloakRole)
                .map(role -> new SimpleGrantedAuthority(role.toAuthority()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        String[] pathParts = roleClaimPath.split("\\.");
        Object current = jwt.getClaims();

        for (String part : pathParts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return Collections.emptyList();
            }
        }

        if (current instanceof List) {
            return (List<String>) current;
        }

        return Collections.emptyList();
    }
}
