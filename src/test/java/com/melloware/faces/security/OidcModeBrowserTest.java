package com.melloware.faces.security;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.OidcProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Real-browser walkthrough of the OIDC profile ({@code mvn -Poidc}): Keycloak login page,
 * the same role-based content as the form profile, and single logout through Keycloak.
 * Needs Docker for Keycloak Dev Services.
 */
@QuarkusTest
@Tag("oidc")
@TestProfile(OidcProfile.class)
@WithPlaywright
class OidcModeBrowserTest {

    @InjectPlaywright
    BrowserContext context;

    @BeforeEach
    void signedOut() {
        context.clearCookies();
    }

    private Page loginAs(String username, String password) {
        Page page = context.newPage();
        page.navigate("/secure/user.xhtml");
        // Unauthenticated -> Quarkus redirects to the Keycloak login page
        assertThat(page).hasURL(Pattern.compile(".*/realms/quarkus-faces/protocol/openid-connect/auth.*"));
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("#kc-login").click();
        return page;
    }

    @Test
    void aliceLogsInThroughKeycloakAndLogsOut() {
        Page page = loginAs("alice", "alice");

        assertThat(page).hasURL(Pattern.compile(".*/secure/user\\.xhtml$"));
        assertThat(page.locator("#profile-username")).hasText("alice");
        assertThat(page.locator("#profile-name")).hasText("Alice Admin");
        assertThat(page.locator("#admin-panel")).isVisible();

        page.navigate("/secure/admin.xhtml");
        assertThat(page.locator("#admin-title")).isVisible();

        // RP-initiated logout via the virtual quarkus.oidc.logout.path -> Keycloak -> post-logout-path
        page.locator("[id='topbar:logout']").click();
        assertThat(page).hasURL(Pattern.compile(".*/index\\.xhtml.*"));
        assertThat(page.locator("[id='topbar:login']")).isVisible();

        // Keycloak session is gone too: secure pages go back to the Keycloak login form
        page.navigate("/secure/user.xhtml");
        assertThat(page.locator("#kc-login")).isVisible();
    }

    @Test
    void servletGetUserPrincipalReturnsKeycloakUser() {
        Page page = loginAs("alice", "alice");
        assertThat(page.locator("#servlet-principal")).hasText("alice");
        assertThat(page.locator("#servlet-admin")).hasText("true");

        // Plain HttpServletRequest#getUserPrincipal / isUserInRole, no Faces involved
        page.navigate("/secure/whoami");
        String body = page.locator("body").innerText();
        assertTrue(body.contains("principal=alice"), body);
        assertTrue(body.contains("remoteUser=alice"), body);
        assertTrue(body.contains("isUserInRole(admin)=true"), body);
        assertTrue(body.contains("isUserInRole(user)=true"), body);
    }

    @Test
    void servletSeesBobWithoutAdminRole() {
        Page page = loginAs("bob", "bob");
        page.navigate("/secure/whoami");
        String body = page.locator("body").innerText();
        assertTrue(body.contains("principal=bob"), body);
        assertTrue(body.contains("isUserInRole(admin)=false"), body);
        assertTrue(body.contains("isUserInRole(user)=true"), body);
    }

    @Test
    void bobHasNoAdminPanelAndIsDeniedAdminConsole() {
        Page page = loginAs("bob", "bob");

        assertThat(page.locator("#profile-username")).hasText("bob");
        assertThat(page.locator("#admin-panel")).hasCount(0);

        page.navigate("/secure/admin.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/access-denied\\.xhtml$"));
        assertThat(page.locator("#denied-title")).hasText("Access denied");
    }
}
