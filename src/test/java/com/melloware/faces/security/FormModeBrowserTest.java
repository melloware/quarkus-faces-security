package com.melloware.faces.security;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.FormProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Real-browser walkthrough of the form login profile ({@code mvn -Pform}):
 * Faces login page, role-based content, path-level denial and logout.
 */
@QuarkusTest
@Tag("form")
@TestProfile(FormProfile.class)
@WithPlaywright
class FormModeBrowserTest {

    @InjectPlaywright
    BrowserContext context;

    @BeforeEach
    void signedOut() {
        context.clearCookies();
    }

    private Page loginAs(String username, String password) {
        Page page = context.newPage();
        page.navigate("/secure/user.xhtml");
        // Unauthenticated -> Quarkus redirects to our Faces login page
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml$"));
        page.locator("#j_username").fill(username);
        page.locator("#j_password").fill(password);
        page.locator("#login-submit").click();
        return page;
    }

    @Test
    void aliceLogsInSeesAdminContentAndLogsOut() {
        Page page = loginAs("alice", "alice");

        // Back on the page originally requested
        assertThat(page).hasURL(Pattern.compile(".*/secure/user\\.xhtml$"));
        assertThat(page.locator("#profile-username")).hasText("alice");
        assertThat(page.locator("#current-user")).hasText("alice");
        assertThat(page.locator("#admin-panel")).isVisible();

        // Path-level check allows admins in
        page.navigate("/secure/admin.xhtml");
        assertThat(page.locator("#admin-title")).isVisible();

        // Logout clears the form-auth cookie and returns home
        page.locator("[id='topbar:logout']").click();
        assertThat(page).hasURL(Pattern.compile(".*/index\\.xhtml\\?logout=true$"));
        assertThat(page.locator("#logout-banner")).isVisible();
        assertThat(page.locator("[id='topbar:login']")).isVisible();

        // ... and secure pages require a login again
        page.navigate("/secure/user.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml$"));
    }

    @Test
    void bobHasNoAdminPanelAndIsDeniedAdminConsole() {
        Page page = loginAs("bob", "bob");

        assertThat(page.locator("#profile-username")).hasText("bob");
        assertThat(page.locator("#admin-panel")).hasCount(0);
        assertThat(page.locator("#no-admin-panel")).isVisible();

        page.navigate("/secure/admin.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/access-denied\\.xhtml$"));
        assertThat(page.locator("#denied-title")).hasText("Access denied");
    }

    @Test
    void wrongPasswordShowsErrorOnLoginPage() {
        Page page = loginAs("alice", "not-the-password");

        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml\\?error=true$"));
        assertThat(page.locator("#login-error")).isVisible();
    }
}
