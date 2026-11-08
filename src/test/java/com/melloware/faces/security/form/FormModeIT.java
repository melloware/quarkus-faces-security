package com.melloware.faces.security.form;

import org.junit.jupiter.api.Tag;

import com.melloware.faces.security.profiles.FormProfile;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/** Runs {@link FormModeTest} against the packaged app (the native executable with {@code -Dnative}). */
@QuarkusIntegrationTest
@Tag("form")
@TestProfile(FormProfile.class)
class FormModeIT extends FormModeTest {
}
