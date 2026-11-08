package com.melloware.faces.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.FormProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * HTTP-level checks for the form login profile ({@code mvn -Pform}).
 */
@QuarkusTest
@Tag("form")
@TestProfile(FormProfile.class)
class FormModeTest {

    private static final String COOKIE = "quarkus-credential";

    /** Posts to j_security_check exactly like login.xhtml does and returns the credential cookie. */
    static String login(String username, String password) {
        String cookie = given().redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("j_username", username)
                .formParam("j_password", password)
                .when().post("/j_security_check")
                .then().statusCode(302)
                .extract().cookie(COOKIE);
        assertNotNull(cookie, "form login should set the " + COOKIE + " cookie");
        return cookie;
    }

    @Test
    void homePageIsPublicAndShowsMode() {
        given().when().get("/index.xhtml")
                .then().statusCode(200)
                .body(containsString("Form login"))
                .body(not(containsString("unsecured-banner")));
    }

    @Test
    void anonymousIsRedirectedToFacesLoginPage() {
        given().redirects().follow(false)
                .when().get("/secure/user.xhtml")
                .then().statusCode(302)
                .header("Location", endsWith("/login.xhtml"));
    }

    @Test
    void loginPageRenders() {
        given().when().get("/login.xhtml")
                .then().statusCode(200)
                .body(containsString("j_security_check"))
                .body(containsString("name=\"j_username\""));
    }

    @Test
    void wrongPasswordGoesToErrorPage() {
        given().redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("j_username", "alice")
                .formParam("j_password", "wrong")
                .when().post("/j_security_check")
                .then().statusCode(302)
                .header("Location", endsWith("/login.xhtml?error=true"));
    }

    @Test
    void aliceSeesUserPageWithAdminPanel() {
        String cookie = login("alice", "alice");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"profile-username\">alice<"))
                .body(containsString("id=\"admin-panel\""));
    }

    @Test
    void aliceCanOpenAdminConsole() {
        String cookie = login("alice", "alice");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/admin.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"admin-title\""));
    }

    @Test
    void bobSeesUserPageWithoutAdminPanel() {
        String cookie = login("bob", "bob");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"profile-username\">bob<"))
                .body(not(containsString("id=\"admin-panel\"")))
                .body(containsString("id=\"no-admin-panel\""));
    }

    // ------------------------------------------------ HttpServletRequest#getUserPrincipal

    @Test
    void servletSeesAlicePrincipalAndRoles() {
        String cookie = login("alice", "alice");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/whoami")
                .then().statusCode(200)
                .body(containsString("principal=alice"))
                .body(containsString("remoteUser=alice"))
                .body(containsString("isUserInRole(admin)=true"))
                .body(containsString("isUserInRole(user)=true"));
    }

    @Test
    void servletSeesBobPrincipalWithoutAdminRole() {
        String cookie = login("bob", "bob");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/whoami")
                .then().statusCode(200)
                .body(containsString("principal=bob"))
                .body(containsString("isUserInRole(admin)=false"))
                .body(containsString("isUserInRole(user)=true"));
    }

    @Test
    void facesPageSeesServletPrincipal() {
        String cookie = login("alice", "alice");
        given().cookie(COOKIE, cookie)
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"servlet-principal\">alice<"))
                .body(containsString("id=\"servlet-admin\">true<"));
    }

    @Test
    void anonymousWhoAmIRedirectsToLogin() {
        given().redirects().follow(false)
                .when().get("/secure/whoami")
                .then().statusCode(302)
                .header("Location", endsWith("/login.xhtml"));
    }

    @Test
    void bobIsDeniedAdminConsoleWithFriendlyPage() {
        String cookie = login("bob", "bob");
        given().cookie(COOKIE, cookie).redirects().follow(false)
                .when().get("/secure/admin.xhtml")
                .then().statusCode(302)
                .header("Location", endsWith("/access-denied.xhtml"));
    }
}
