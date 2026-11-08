package com.melloware.faces.security.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Runs the app with application-form.properties layered on top (same as {@code mvn -Pform}). */
public class FormProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "form";
    }
}
