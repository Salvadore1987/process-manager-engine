package uz.salvadore.processengine.security.model;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ProcessEngineRole {

    PROCESS_ADMIN("process-admin"),
    PROCESS_OPERATOR("process-operator"),
    PROCESS_VIEWER("process-viewer"),
    PROCESS_DEPLOYER("process-deployer");

    private static final Map<String, ProcessEngineRole> BY_KEYCLOAK_ROLE =
            Stream.of(values()).collect(Collectors.toMap(
                    ProcessEngineRole::getKeycloakRole, role -> role));

    private final String keycloakRole;

    ProcessEngineRole(String keycloakRole) {
        this.keycloakRole = keycloakRole;
    }

    public String getKeycloakRole() {
        return keycloakRole;
    }

    public String toAuthority() {
        return "ROLE_" + name();
    }

    public static ProcessEngineRole fromKeycloakRole(String keycloakRole) {
        ProcessEngineRole role = BY_KEYCLOAK_ROLE.get(keycloakRole);
        if (role == null) {
            throw new IllegalArgumentException("Unknown Keycloak role: " + keycloakRole);
        }
        return role;
    }

    public static boolean isKnownKeycloakRole(String keycloakRole) {
        return BY_KEYCLOAK_ROLE.containsKey(keycloakRole);
    }
}
