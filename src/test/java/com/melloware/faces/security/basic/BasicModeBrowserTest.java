package com.melloware.faces.security.basic;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.melloware.faces.security.profiles.BasicProfile;
import com.melloware.faces.security.basic.BasicLogoutServlet;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Real-browser walkthrough of the Basic auth profile ({@code mvn -Pbasic}).
 * <p>
 * A headless browser can't type into the native Basic auth dialog, so each test opens a browser
 * context with Playwright {@code httpCredentials}. Like a real user, the browser only sends them
 * after the server's {@code 401} + {@code WWW-Authenticate: Basic} challenge.
 */
@QuarkusTest
@Tag("basic")
@TestProfile(BasicProfile.class)
@WithPlaywright
class BasicModeBrowserTest {

    @InjectPlaywright
    BrowserContext context;

    @TestHTTPResource("/")
    URL baseUrl;

    private final List<BrowserContext> opened = new ArrayList<>();

    @AfterEach
    void closeContexts() {
        opened.forEach(BrowserContext::close);
        opened.clear();
    }

    /** A fresh browser whose Basic auth dialog will be answered with these credentials. */
    private Page browserAs(String username, String password) {
        Browser browser = context.browser();
        BrowserContext withCredentials = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl.toString())
                .setHttpCredentials(username, password));
        opened.add(withCredentials);
        return withCredentials.newPage();
    }

    @Test
    void anonymousBrowserIsChallengedInsteadOfRedirected() {
        context.clearCookies();
        Page page = context.newPage();
        Response response = page.navigate("/secure/user.xhtml");
        assertEquals(401, response.status());
        assertTrue(response.headerValue("www-authenticate").toLowerCase().startsWith("basic realm="),
                response.headers().toString());
        page.close();
    }

    @Test
    void aliceAnswersTheChallengeAndSeesAdminContent() {
        Page page = browserAs("alice", "alice");
        Response response = page.navigate("/secure/user.xhtml");

        assertEquals(200, response.status());
        assertThat(page.locator("#profile-username")).hasText("alice");
        assertThat(page.locator("#current-user")).hasText("alice");
        assertThat(page.locator("#admin-panel")).isVisible();
        assertThat(page.locator("#basic-session")).isVisible();

        page.navigate("/secure/admin.xhtml");
        assertThat(page.locator("#admin-title")).isVisible();

        page.navigate("/secure/whoami");
        String body = page.locator("body").innerText();
        assertTrue(body.contains("principal=alice"), body);
        assertTrue(body.contains("isUserInRole(admin)=true"), body);
    }

    @Test
    void bobHasNoAdminPanelAndIsDeniedAdminConsole() {
        Page page = browserAs("bob", "bob");
        page.navigate("/secure/user.xhtml");

        assertThat(page.locator("#profile-username")).hasText("bob");
        assertThat(page.locator("#admin-panel")).hasCount(0);
        assertThat(page.locator("#no-admin-panel")).isVisible();

        page.navigate("/secure/admin.xhtml");
        assertThat(page).hasURL(Pattern.compile(".*/access-denied\\.xhtml$"));
        assertThat(page.locator("#denied-title")).hasText("Access denied");
    }

    @Test
    void wrongPasswordIsRejected() {
        Page page = browserAs("alice", "not-the-password");
        Response response = page.navigate("/secure/user.xhtml");
        assertEquals(401, response.status());
    }

    @Test
    void logoutChallengesTheBrowserAgain() {
        Page page = browserAs("alice", "alice");
        page.navigate("/secure/user.xhtml");
        assertThat(page.locator("#current-user")).hasText("alice");

        List<String> logoutCookies = new ArrayList<>();
        page.onRequest(r -> {
            if (r.url().contains(BasicLogoutServlet.PATH)) {
                logoutCookies.add(String.valueOf(r.allHeaders().get("cookie")));
            }
        });
        page.locator("[id='topbar:logout']").click();

        // Logout goes to BasicLogoutServlet with the one-time cookie, which answers the cached
        // credentials with a 401 (see BasicModeTest) so a real browser forgets them and shows its
        // dialog. Chromium handles that 401 internally and Playwright "types" the credentials
        // again, so here alice is simply signed back in.
        assertThat(page).hasURL(Pattern.compile(".*/secure/user\\.xhtml$"));
        assertThat(page.locator("#current-user")).hasText("alice");
        assertTrue(logoutCookies.stream().anyMatch(c -> c.contains(BasicLogoutServlet.PENDING_COOKIE)),
                logoutCookies.toString());
    }
}
