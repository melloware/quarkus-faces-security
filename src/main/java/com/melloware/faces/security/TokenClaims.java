package com.melloware.faces.security;

/**
 * Token claims about the current user (name, email, issuer, ...).
 * <p>
 * Only OIDC has tokens. The OIDC implementation lives in {@code src/main/oidc/java} and is compiled
 * only by the {@code oidc} Maven profile, so the unsecured and basic builds never need the
 * {@code quarkus-oidc} / MicroProfile JWT JARs. Without it, {@link NoTokenClaims} is used.
 */
public interface TokenClaims {

    /** @return the claim as a string, or {@code null} if there is no token or no such claim */
    String claim(String name);

    /** @return the token issuer, or {@code null} */
    String issuer();

    /** @return token expiry in epoch seconds, or {@code 0} if unknown */
    long expiresAt();
}
