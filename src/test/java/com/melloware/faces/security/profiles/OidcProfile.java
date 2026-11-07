package com.melloware.faces.security.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Runs the app with application-oidc.properties layered on top (same as {@code mvn -Poidc}).
 * Keycloak Dev Services starts a Keycloak container, so Docker (or Podman) must be running.
 */
public class OidcProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "oidc";
    }
}
