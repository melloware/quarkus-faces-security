package com.melloware.faces.security;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * The ONE place in the app that knows which security mode is active.
 * <p>
 * Every page talks only to this bean ({@code #{securityService...}}), so the same
 * XHTML works unchanged whether the app runs unsecured, with form login, or with OIDC.
 * It has no compile-time dependency on OIDC: token claims come through {@link TokenClaims}.
 */
@Named
@RequestScoped
public class SecurityService {

    private static final Logger LOG = Logger.getLogger(SecurityService.class);

    public static final String MODE_NONE = "none";
    public static final String MODE_BASIC = "basic";
    public static final String MODE_OIDC = "oidc";

    @Inject
    SecurityIdentity identity;

    @Inject
    TokenClaims claims;

    @ConfigProperty(name = "demo.security.mode", defaultValue = MODE_NONE)
    String mode;

    @ConfigProperty(name = "quarkus.oidc.logout.path", defaultValue = "/secure/logout")
    String oidcLogoutPath;

    // ---------------------------------------------------------------- mode

    public String getMode() {
        return mode;
    }

    public boolean isSecured() {
        return !MODE_NONE.equals(mode);
    }

    public boolean isBasic() {
        return MODE_BASIC.equals(mode);
    }

    public boolean isOidc() {
        return MODE_OIDC.equals(mode);
    }

    public String getModeLabel() {
        return switch (mode) {
            case MODE_BASIC -> "Form login";
            case MODE_OIDC -> "OIDC (Keycloak)";
            default -> "Unsecured";
        };
    }

    /** PrimeFaces severity used for the mode badge. */
    public String getModeSeverity() {
        return switch (mode) {
            case MODE_BASIC -> "info";
            case MODE_OIDC -> "success";
            default -> "warning";
        };
    }

    public String getModeIcon() {
        return switch (mode) {
            case MODE_BASIC -> "pi pi-id-card";
            case MODE_OIDC -> "pi pi-key";
            default -> "pi pi-lock-open";
        };
    }

    /** Human readable description of how the user was authenticated. */
    public String getMechanism() {
        return switch (mode) {
            case MODE_BASIC -> "Quarkus form authentication (j_security_check) + embedded users";
            case MODE_OIDC -> "OpenID Connect authorization code flow (Keycloak)";
            default -> "None";
        };
    }

    // ---------------------------------------------------------------- identity

    public boolean isLoggedIn() {
        return identity != null && !identity.isAnonymous();
    }

    public String getUsername() {
        if (!isLoggedIn()) {
            return "anonymous";
        }
        String preferred = claims.claim("preferred_username");
        return preferred != null ? preferred : identity.getPrincipal().getName();
    }

    /** Full name from the token when available, otherwise the capitalized username. */
    public String getDisplayName() {
        String name = claims.claim("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = getUsername();
        return username.isEmpty() ? username : Character.toUpperCase(username.charAt(0)) + username.substring(1);
    }

    public String getInitials() {
        String[] parts = getDisplayName().trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }

    /** Roles sorted with the demo's own roles (admin, user) first. */
    public List<String> getRoles() {
        List<String> roles = new ArrayList<>(identity.getRoles());
        roles.sort((a, b) -> {
            int ra = isAppRole(a) ? 0 : 1;
            int rb = isAppRole(b) ? 0 : 1;
            return ra != rb ? Integer.compare(ra, rb) : a.compareTo(b);
        });
        return roles;
    }

    public boolean isAppRole(String role) {
        return "admin".equals(role) || "user".equals(role);
    }

    public boolean hasRole(String role) {
        return isLoggedIn() && identity.hasRole(role);
    }

    public boolean isAdmin() {
        return hasRole("admin");
    }

    // ---------------------------------------------------------------- OIDC token details (null otherwise)

    public String getEmail() {
        return claims.claim("email");
    }

    public String getIssuer() {
        return claims.issuer();
    }

    public Date getTokenExpiry() {
        long exp = claims.expiresAt();
        return exp == 0 ? null : Date.from(Instant.ofEpochSecond(exp));
    }

    public TimeZone getTimeZone() {
        return TimeZone.getDefault();
    }

    // ---------------------------------------------------------------- logout

    /**
     * Logs the user out in whatever way the active mode requires.
     * <ul>
     * <li><b>basic</b>: remove the encrypted form-auth cookie server side, then go home.</li>
     * <li><b>oidc</b>: redirect to the virtual {@code quarkus.oidc.logout.path}; Quarkus then
     * performs RP-initiated logout at Keycloak and returns to {@code post-logout-path}.</li>
     * </ul>
     */
    public void logout() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext external = context.getExternalContext();
        String contextPath = external.getRequestContextPath();
        LOG.infof("Logging out [%s] (mode=%s)", getUsername(), mode);

        if (isOidc()) {
            external.redirect(contextPath + oidcLogoutPath);
            return;
        }
        if (isBasic() && isLoggedIn()) {
            FormAuthenticationMechanism.logout(identity);
        }
        external.invalidateSession();
        external.redirect(contextPath + "/index.xhtml?logout=true");
    }
}
