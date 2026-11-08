package com.melloware.faces.security.basic;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.BasicProfile;
import com.melloware.faces.security.basic.BasicLogoutServlet;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * HTTP-level checks for the Basic auth profile ({@code mvn -Pbasic}).
 */
@QuarkusTest
@Tag("basic")
@TestProfile(BasicProfile.class)
class BasicModeTest {

    /** The auth scheme is case-insensitive; Quarkus sends "basic". */
    private static final String CHALLENGE = "Basic realm=\"Quarkus Faces\"";

    @Test
    void homePageIsPublicAndShowsMode() {
        given().when().get("/index.xhtml")
                .then().statusCode(200)
                .body(containsString("Basic auth"))
                .body(not(containsString("unsecured-banner")));
    }

    @Test
    void anonymousGetsBasicChallengeNotLoginPage() {
        given().redirects().follow(false)
                .when().get("/secure/user.xhtml")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalToIgnoringCase(CHALLENGE));
    }

    @Test
    void wrongPasswordIsChallengedAgain() {
        given().auth().preemptive().basic("alice", "wrong").redirects().follow(false)
                .when().get("/secure/user.xhtml")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalToIgnoringCase(CHALLENGE));
    }

    @Test
    void formLoginIsNotEnabled() {
        given().redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("j_username", "alice")
                .formParam("j_password", "alice")
                .when().post("/j_security_check")
                .then().statusCode(not(equalTo(302)));
    }

    @Test
    void aliceSeesUserPageWithAdminPanel() {
        given().auth().preemptive().basic("alice", "alice")
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"profile-username\">alice<"))
                .body(containsString("id=\"admin-panel\""))
                .body(containsString("id=\"basic-session\""));
    }

    @Test
    void aliceCanOpenAdminConsole() {
        given().auth().preemptive().basic("alice", "alice")
                .when().get("/secure/admin.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"admin-title\""))
                .body(containsString("application-basic.properties"));
    }

    @Test
    void bobSeesUserPageWithoutAdminPanel() {
        given().auth().preemptive().basic("bob", "bob")
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"profile-username\">bob<"))
                .body(not(containsString("id=\"admin-panel\"")))
                .body(containsString("id=\"no-admin-panel\""));
    }

    @Test
    void bobIsDeniedAdminConsoleWithFriendlyPage() {
        given().auth().preemptive().basic("bob", "bob").redirects().follow(false)
                .when().get("/secure/admin.xhtml")
                .then().statusCode(302)
                .header("Location", endsWith("/access-denied.xhtml"));
    }

    // ------------------------------------------------ HttpServletRequest#getUserPrincipal

    @Test
    void servletSeesAlicePrincipalAndRoles() {
        given().auth().preemptive().basic("alice", "alice")
                .when().get("/secure/whoami")
                .then().statusCode(200)
                .body(containsString("principal=alice"))
                .body(containsString("remoteUser=alice"))
                .body(containsString("isUserInRole(admin)=true"))
                .body(containsString("isUserInRole(user)=true"));
    }

    @Test
    void servletSeesBobPrincipalWithoutAdminRole() {
        given().auth().preemptive().basic("bob", "bob")
                .when().get("/secure/whoami")
                .then().statusCode(200)
                .body(containsString("principal=bob"))
                .body(containsString("isUserInRole(admin)=false"))
                .body(containsString("isUserInRole(user)=true"));
    }

    @Test
    void facesPageSeesServletPrincipal() {
        given().auth().preemptive().basic("alice", "alice")
                .when().get("/secure/user.xhtml")
                .then().statusCode(200)
                .body(containsString("id=\"servlet-principal\">alice<"))
                .body(containsString("id=\"servlet-admin\">true<"));
    }

    // ------------------------------------------------ logout

    @Test
    void logoutRejectsCachedCredentialsSoTheBrowserForgetsThem() {
        given().auth().preemptive().basic("alice", "alice").redirects().follow(false)
                .cookie(BasicLogoutServlet.PENDING_COOKIE, "true")
                .when().get(BasicLogoutServlet.PATH)
                .then().statusCode(401)
                .header("WWW-Authenticate", equalToIgnoringCase(CHALLENGE))
                .header("Set-Cookie", containsString(BasicLogoutServlet.PENDING_COOKIE + "="))
                .header("Set-Cookie", containsString("Max-Age=0"))
                // shown when the user cancels the browser dialog: sends them home
                .body(containsString("/index.xhtml?logout=true"));
    }

    @Test
    void reenteringCredentialsAfterLogoutSignsBackIn() {
        // No pending-logout cookie: the user typed their credentials into the dialog again
        given().auth().preemptive().basic("alice", "alice").redirects().follow(false)
                .when().get(BasicLogoutServlet.PATH)
                .then().statusCode(302)
                .header("Location", endsWith("/secure/user.xhtml"));
    }

    @Test
    void anonymousLogoutIsChallenged() {
        given().redirects().follow(false)
                .when().get(BasicLogoutServlet.PATH)
                .then().statusCode(401);
    }
}
