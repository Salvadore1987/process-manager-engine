package uz.salvadore.processengine.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "process-engine.security")
public class SecurityProperties {

    private boolean enabled = true;
    private String issuerUri;
    private String jwkSetUri;
    private String roleClaimPath = "realm_access.roles";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getRoleClaimPath() {
        return roleClaimPath;
    }

    public void setRoleClaimPath(String roleClaimPath) {
        this.roleClaimPath = roleClaimPath;
    }
}
