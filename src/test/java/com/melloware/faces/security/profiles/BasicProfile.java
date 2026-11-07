package com.melloware.faces.security.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Runs the app with application-basic.properties layered on top (same as {@code mvn -Pbasic}). */
public class BasicProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "basic";
    }
}
