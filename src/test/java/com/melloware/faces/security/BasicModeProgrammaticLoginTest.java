package com.melloware.faces.security;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.BasicProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Programmatic login from a Faces action ({@code LoginController#login}), the "Faces action" tab
 * of the login page in the form login profile ({@code mvn -Pbasic}).
 */
@QuarkusTest
@Tag("basic")
@TestProfile(BasicProfile.class)
@WithPlaywright
class BasicModeProgrammaticLoginTest {

    private static final String USERNAME = "[id='loginTabs:progForm:username']";
    private static final String PASSWORD = "[id='loginTabs:progForm:password']";
    private static final String SUBMIT = "[id='loginTabs:progForm:progSubmit']";
    private static final String MESSAGES = "[id='loginTabs:progForm:progMessages']";

    @InjectPlaywright
    BrowserContext context;

    @BeforeEach
    void signedOut() {
        context.clearCookies();
    }

    private void submit(Page page, String username, String password) {
        page.locator(USERNAME).fill(username);
        page.locator(PASSWORD).fill(password);
        page.locator(SUBMIT).click();
    }

    @Test
    void returnsToOriginallyRequestedPage() {
        Page page = context.newPage();
        page.navigate("/secure/admin.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml$"));

        page.locator("[id='loginTabs:facesTab_header']").click();
        submit(page, "alice", "alice");

        // Quarkus' redirect-location cookie is honored, just like j_security_check
        assertThat(page).hasURL(Pattern.compile(".*/secure/admin\\.xhtml$"));
        assertThat(page.locator("#admin-title")).isVisible();
        assertThat(page.locator("#current-user")).hasText("alice");
    }

    @Test
    void goesToLandingPageAndServletSeesPrincipal() {
        Page page = context.newPage();
        page.navigate("/login.xhtml?tab=faces");
        submit(page, "bob", "bob");

        assertThat(page).hasURL(Pattern.compile(".*/secure/user\\.xhtml$"));
        assertThat(page.locator("#profile-username")).hasText("bob");
        assertThat(page.locator("#admin-panel")).hasCount(0);

        // The cookie is a normal form-auth cookie: the servlet container sees the same user
        page.navigate("/secure/whoami");
        String body = page.locator("body").innerText();
        assertTrue(body.contains("principal=bob"), body);
        assertTrue(body.contains("isUserInRole(admin)=false"), body);

        // and the path-level admin check still applies
        page.navigate("/secure/admin.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/access-denied\\.xhtml$"));
    }

    @Test
    void wrongPasswordShowsFacesMessageAndStaysOnPage() {
        Page page = context.newPage();
        page.navigate("/login.xhtml?tab=faces");
        submit(page, "alice", "wrong");

        assertThat(page.locator(MESSAGES)).containsText("Invalid username or password");
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml.*"));
        page.navigate("/secure/user.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml$"));
    }

    @Test
    void facesValidationRunsBeforeLogin() {
        Page page = context.newPage();
        page.navigate("/login.xhtml?tab=faces");
        page.locator(SUBMIT).click();

        assertThat(page.locator(MESSAGES)).containsText("Username is required");
        assertThat(page.locator(MESSAGES)).containsText("Password is required");
    }

    @Test
    void logoutWorksAfterProgrammaticLogin() {
        Page page = context.newPage();
        page.navigate("/login.xhtml?tab=faces");
        submit(page, "alice", "alice");
        assertThat(page.locator("#current-user")).hasText("alice");

        page.locator("[id='topbar:logout']").click();
        assertThat(page).hasURL(Pattern.compile(".*/index\\.xhtml\\?logout=true$"));
        page.navigate("/secure/user.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/login\\.xhtml$"));
    }
}
