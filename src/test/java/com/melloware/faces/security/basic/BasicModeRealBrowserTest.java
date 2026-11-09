package com.melloware.faces.security.basic;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.melloware.faces.security.profiles.BasicProfile;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Basic auth in Chromium and Firefox (plus WebKit on macOS) using each browser's <b>own</b> credential cache.
 * <p>
 * {@link BasicModeBrowserTest} uses Playwright {@code httpCredentials}, which answers every challenge
 * itself and hides how real browsers scope and forget Basic credentials. Here the credentials are put
 * in the URL instead ({@code http://alice:alice@host/...}), which fills the browser's cache the same
 * way typing them into the native dialog does. A headless browser cancels the native dialog, so a
 * {@code 401} challenge on a page navigation is where a real user would see that dialog.
 */
@QuarkusTest
@Tag("basic")
@TestProfile(BasicProfile.class)
class BasicModeRealBrowserTest {

    @TestHTTPResource("/")
    URL baseUrl;

    private static Playwright playwright;

    @BeforeAll
    static void startPlaywright() {
        playwright = Playwright.create();
    }

    @AfterAll
    static void stopPlaywright() {
        if (playwright != null) {
            playwright.close();
        }
    }

    /**
     * Chromium and Firefox share their network stack with Chrome and Firefox. Playwright's WebKit uses
     * Apple's network stack (as Safari does) only on macOS; on Windows and Linux it uses curl/libsoup,
     * which handle Basic credentials differently, so there it would say nothing about Safari.
     */
    static List<String> browsers() {
        List<String> browsers = new ArrayList<>(List.of("chromium", "firefox"));
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            browsers.add("webkit");
        }
        return browsers;
    }

    private Browser launch(String name) {
        return switch (name) {
            // Firefox asks for confirmation before using credentials in a URL unless this pref allows it.
            case "firefox" -> playwright.firefox().launch(new BrowserType.LaunchOptions()
                    .setFirefoxUserPrefs(Map.of("network.http.phishy-userpass-length", 255)));
            case "webkit" -> playwright.webkit().launch();
            default -> playwright.chromium().launch();
        };
    }

    private String url(String path) {
        return "http://" + baseUrl.getHost() + ":" + baseUrl.getPort() + path;
    }

    /** Clicks the topbar Login button the way a user would and answers the dialog as alice. */
    private void signInAsAliceFromHome(Page page) {
        page.navigate(url("/index.xhtml"));
        String href = page.locator("[id='topbar:login']").locator("xpath=descendant-or-self::a").first()
                .getAttribute("href");
        Response response = page.navigate("http://alice:alice@" + baseUrl.getHost() + ":" + baseUrl.getPort() + href);
        assertEquals(200, response.status(), "login via " + href);
        assertThat(page.locator("#current-user")).hasText("alice");
    }

    @ParameterizedTest
    @MethodSource("browsers")
    void homePageShowsSignedInUserAfterLogin(String browserName) {
        try (Browser browser = launch(browserName); BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            signInAsAliceFromHome(page);

            page.navigate(url("/index.xhtml"));
            assertThat(page.locator("#current-user")).hasText("alice");
            assertThat(page.locator("[id='topbar:logout']")).isVisible();
        }
    }

    @ParameterizedTest
    @MethodSource("browsers")
    void logoutSignsOutWithoutShowingTheLoginDialog(String browserName) {
        try (Browser browser = launch(browserName); BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            signInAsAliceFromHome(page);

            List<String> challenges = new ArrayList<>();
            page.onResponse(r -> {
                if (r.request().isNavigationRequest() && r.status() == 401) {
                    challenges.add(r.url());
                }
            });
            page.locator("[id='topbar:logout']").click();
            assertThat(page).hasURL(Pattern.compile(".*/index\\.xhtml\\?logout=true$"));
            assertThat(page.locator("[id='topbar:login']")).isVisible();
            assertTrue(challenges.isEmpty(), "login dialog shown during logout: " + challenges);

            // The browser really forgot alice: the secure page challenges again.
            assertChallenged(page, "/secure/user.xhtml");
        }
    }

    /** A challenged navigation is a 401 page, except in Firefox, which aborts it when the dialog is cancelled. */
    private void assertChallenged(Page page, String path) {
        try {
            Response response = page.navigate(url(path));
            assertEquals(401, response.status());
        } catch (PlaywrightException e) {
            assertTrue(e.getMessage().contains("NS_ERROR_NET_EMPTY_RESPONSE"), e.getMessage());
        }
    }
}
