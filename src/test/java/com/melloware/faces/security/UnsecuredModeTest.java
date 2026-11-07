package com.melloware.faces.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Default config profile (no security profile selected), same as plain {@code mvn quarkus:dev}.
 */
@QuarkusTest
@Tag("unsecured")
class UnsecuredModeTest {

    @Test
    void homePageShowsWarningBanner() {
        given().when().get("/index.xhtml")
                .then().statusCode(200)
                .body(containsString("unsecured-banner"))
                .body(containsString("No security profile selected"))
                .body(containsString("Unsecured"))
                .body(not(containsString("id=\"topbar:logout\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/secure/user.xhtml", "/secure/admin.xhtml" })
    void securePagesAreBlockedAndRedirectHome(String path) {
        given().redirects().follow(false)
                .when().get(path)
                .then().statusCode(302)
                .header("Location", endsWith("/index.xhtml?denied=true"));
    }

    @Test
    void deniedBannerExplainsWhy() {
        given().when().get("/index.xhtml?denied=true")
                .then().statusCode(200)
                .body(containsString("denied-banner"))
                .body(containsString("no security profile is selected"));
    }
}
