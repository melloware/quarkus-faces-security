package com.melloware.faces.security;

import org.junit.jupiter.api.Tag;

import com.melloware.faces.security.profiles.FormProfile;

import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/** Runs {@link FormModeBrowserTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("form")
@TestProfile(FormProfile.class)
@WithPlaywright
class FormModeBrowserIT extends FormModeBrowserTest {
}
