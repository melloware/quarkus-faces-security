package com.melloware.faces.security;

import org.junit.jupiter.api.Tag;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/** Runs {@link UnsecuredModeTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("unsecured")
class UnsecuredModeIT extends UnsecuredModeTest {
}
