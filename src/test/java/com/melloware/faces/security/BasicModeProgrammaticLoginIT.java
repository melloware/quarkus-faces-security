package com.melloware.faces.security;

import org.junit.jupiter.api.Tag;

import com.melloware.faces.security.profiles.BasicProfile;

import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/** Runs {@link BasicModeProgrammaticLoginTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("basic")
@TestProfile(BasicProfile.class)
@WithPlaywright
class BasicModeProgrammaticLoginIT extends BasicModeProgrammaticLoginTest {
}
