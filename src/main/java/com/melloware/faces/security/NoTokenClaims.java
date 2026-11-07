package com.melloware.faces.security;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/** Used in the unsecured and basic modes, where there is no token. Replaced by the OIDC bean when present. */
@DefaultBean
@ApplicationScoped
public class NoTokenClaims implements TokenClaims {

    @Override
    public String claim(String name) {
        return null;
    }

    @Override
    public String issuer() {
        return null;
    }

    @Override
    public long expiresAt() {
        return 0;
    }
}
