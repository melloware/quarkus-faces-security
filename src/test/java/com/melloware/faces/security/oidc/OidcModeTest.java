package com.melloware.faces.security.oidc;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.OidcProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * HTTP-level checks for the OIDC profile ({@code mvn -Poidc}). Needs Docker for Keycloak Dev Services.
 */
@QuarkusTest
@Tag("oidc")
@TestProfile(OidcProfile.class)
class OidcModeTest {

    @Test
    void homePageIsPublicAndShowsMode() {
        given().when().get("/index.xhtml")
                .then().statusCode(200)
                .body(containsString("OIDC (Keycloak)"))
                .body(not(containsString("unsecured-banner")));
    }

    @Test
    void anonymousIsRedirectedToKeycloak() {
        given().redirects().follow(false)
                .when().get("/secure/user.xhtml")
                .then().statusCode(302)
                .header("Location", containsString("/realms/quarkus-faces/protocol/openid-connect/auth"))
                .header("Location", containsString("client_id=quarkus-faces"));
    }
}
