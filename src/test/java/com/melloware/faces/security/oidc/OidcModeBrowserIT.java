package com.melloware.faces.security.oidc;

import org.junit.jupiter.api.Tag;

import com.melloware.faces.security.profiles.OidcProfile;

import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/** Runs {@link OidcModeBrowserTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("oidc")
@TestProfile(OidcProfile.class)
@WithPlaywright
class OidcModeBrowserIT extends OidcModeBrowserTest {
}
