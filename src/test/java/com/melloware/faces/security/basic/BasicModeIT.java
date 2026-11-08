package com.melloware.faces.security.basic;

import org.junit.jupiter.api.Tag;

import com.melloware.faces.security.profiles.BasicProfile;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/** Runs {@link BasicModeTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("basic")
@TestProfile(BasicProfile.class)
class BasicModeIT extends BasicModeTest {
}
