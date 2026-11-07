package com.melloware.faces.security.oidc;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.melloware.faces.security.TokenClaims;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Reads claims from the OIDC ID token. Compiled ONLY by the {@code oidc} Maven profile
 * (build-helper adds {@code src/main/oidc/java}), because it needs the OIDC / MicroProfile JWT JARs.
 */
@RequestScoped
public class OidcTokenClaims implements TokenClaims {

    @Inject
    SecurityIdentity identity;

    private JsonWebToken token() {
        return identity.getPrincipal() instanceof JsonWebToken jwt ? jwt : null;
    }

    @Override
    public String claim(String name) {
        JsonWebToken jwt = token();
        Object value = jwt == null ? null : jwt.getClaim(name);
        return value == null ? null : value.toString();
    }

    @Override
    public String issuer() {
        JsonWebToken jwt = token();
        return jwt == null ? null : jwt.getIssuer();
    }

    @Override
    public long expiresAt() {
        JsonWebToken jwt = token();
        return jwt == null ? 0 : jwt.getExpirationTime();
    }
}
