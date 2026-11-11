# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A demo Quarkus + Jakarta Faces (MyFaces, PrimeFaces, OmniFaces) app. The same pages, users and roles run unsecured, behind Quarkus form login, behind browser HTTP Basic auth, or behind OIDC/Keycloak, depending on the Maven profile. See README.md for the full walkthrough. The design spec is in `docs/superpowers/specs/`.

## Commands

```bash
mvn quarkus:dev            # unsecured mode (public page only, /secure/** redirected home)
mvn -Pform                 # form login + embedded users  (profile defaultGoal = clean quarkus:dev)
mvn -Pbasic                # HTTP Basic auth (browser dialog) + embedded users  (defaultGoal = clean quarkus:dev)
mvn -Poidc                 # OIDC via Keycloak Dev Services (needs Docker); defaultGoal = clean compile quarkus:dev
mvn verify                 # unsecured tests only (@Tag("unsecured"))
mvn -Pform verify          # form login tests only (@Tag("form"))
mvn -Pbasic verify         # Basic auth tests only (@Tag("basic"))
mvn -Poidc verify          # OIDC tests only (@Tag("oidc")) - Docker must be running (fails, not skips, without it)
mvn -Pform verify -Dtest='FormModeBrowserTest#bobHasNoAdminPanelAndIsDeniedAdminConsole'   # single test (needs its profile)
mvn -Pform verify -Dnative -Dnative.surefire.skip    # native executable + *IT tests for a mode (CI: .github/workflows/build.yml)
mvn -Pform verify -DskipITs=false -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false   # *IT tests against the JVM jar (quick check of IT plumbing)
```

Each mode has different JARs on the classpath, so there is no single build that runs every test. Always pass the matching `-P` profile when running a mode's tests.

On Windows, stopping `mvn quarkus:dev` from a background shell can leave the forked dev JVM running and holding `target/*-dev.jar`, which makes the next `clean` fail. Kill the leftover `java.exe` running `quarkus-faces-security-dev.jar`.

## Architecture

- **Each Maven profile brings only its own security JARs.** The base build has only `quarkus-security`. `-Pform` and `-Pbasic` both add `quarkus-elytron-security-properties-file` (embedded users). `-Poidc` adds `quarkus-oidc` and `quarkus-jackson`, plus the source folder `src/main/oidc/java` via build-helper. The profiles also set `<quarkus.profile>dev,form|basic|oidc</quarkus.profile>`, which layers `application-form.properties` / `application-basic.properties` / `application-oidc.properties` over the base `application.properties` (security off, `demo.security.mode=none`). Don't put `quarkus.oidc.*` or `quarkus.security.users.*` keys in the base file; they'd be unknown keys in the other builds.
- **Packages:** classes used by every mode live in `com.melloware.faces.security`. A class (or test) that only one mode uses goes in its subpackage: `.form` (`LoginController`), `.basic` (`BasicLoginServlet`, `BasicLogoutServlet`), `.oidc` (`OidcTokenClaims`, in the `src/main/oidc/java` source folder). The tests are split the same way (`form/`, `basic/`, `oidc/`); unsecured tests and `profiles/` stay at the root.
- **No OIDC types in `src/main/java`.** Anything that imports `org.eclipse.microprofile.jwt`/`io.quarkus.oidc` goes in `src/main/oidc/java`. `SecurityService` reads claims via the `TokenClaims` interface: `NoTokenClaims` is the `@DefaultBean`, and `OidcTokenClaims` (OIDC folder) replaces it.
- **Tests:** JUnit `@Tag("unsecured"|"form"|"basic"|"oidc")` plus surefire `<groups>${test.groups}</groups>`, set by each profile. Each class also uses `QuarkusTestProfile.getConfigProfile()` (`profiles/FormProfile`, `profiles/BasicProfile`, `profiles/OidcProfile`). Every new test class needs both the tag and the matching profile, plus an `*IT` subclass (`@QuarkusIntegrationTest`, re-declaring `@Tag`, `@TestProfile` and `@WithPlaywright`) for native mode. `skipITs` is true unless `-Dnative`. In IT mode, Keycloak Dev Services run in the test JVM without `application-oidc.properties`, so the `oidc` profile's failsafe `systemPropertyVariables` duplicate the realm and client settings. Keep the profile property files self-contained, because tests load them without the `dev` profile.
- **`SecurityService` (`@Named @RequestScoped`) is the only class that knows the mode.** XHTML must use `#{securityService...}` (`loggedIn`, `hasRole()`, `logout`, ...) and must never branch on the mechanism directly. `logout()` calls `FormAuthenticationMechanism.logout(identity)` in form mode, redirects to `BasicLogoutServlet` in basic mode, and redirects to the virtual `quarkus.oidc.logout.path` in oidc mode.
- **Servlet API parity:** `WhoAmIServlet` (`/secure/whoami`) reports `getUserPrincipal()`/`getRemoteUser()`/`isUserInRole()`. All three secured modes have tests asserting these match the Quarkus identity. Under OIDC the principal is `preferred_username`.
- **Authorization happens in the Vert.x HTTP layer** (`quarkus.http.auth.permission.*`, duplicated in all three profile files), not in `web.xml`. Faces never sees those 403s, so `SecurityRoutes` (a `@Observes Router` bean) redirects them to `access-denied.xhtml`. In unsecured mode it also redirects `/secure/*` to `/index.xhtml?denied=true`.
- **Form login has two tabs in `login.xhtml`.** "Plain form" is a plain HTML `<form action="j_security_check">`; don't convert it to `h:form`. "Faces action" is a programmatic login: `LoginController.login()` checks credentials with `IdentityProviderManager.authenticateBlocking(...)`, then issues the same `quarkus-credential` cookie via `PersistentLoginManager.save(...)`, built from `VertxHttpConfig` (`auth().form()` + `encryptionKey()`), and redirects using the `quarkus-redirect-location` cookie (path only, to avoid open redirects). `PersistentLoginManager` lives in a Quarkus `runtime` package and is not a documented API; `FormModeProgrammaticLoginTest` guards it on upgrades. Don't reintroduce the #409 HttpClient-to-`j_security_check` hack.
- **Basic auth (`-Pbasic`)** is `quarkus.http.auth.basic=true` with realm `quarkus.http.auth.realm`. There is no login page and no cookie: anonymous requests to `/secure/*` get `401` + `WWW-Authenticate` (Quarkus sends a lowercase `basic` scheme, so compare case-insensitively). Browsers only resend cached credentials to URLs at or below the *directory* of the challenged URL (RFC 7617 §2.2; Firefox/Safari are strict), so Login and "Open the user page" use `#{securityService.loginUrl}`, which is `BasicLoginServlet` (`/basic-login`, a root-level path protected only in `application-basic.properties`, which redirects to the user page). Don't point them at `/secure/...` in basic mode, or public pages show as signed out again. Basic auth has no real logout: `SecurityService.logout()` redirects to `BasicLogoutServlet` (`/basic-logout`, public). Its page runs an `XMLHttpRequest` with made-up `logout:...` credentials against `/basic-login`, which replaces the browser's cached credentials without a dialog, then goes home. Don't go back to answering a page navigation with 401 + challenge: real browsers show the dialog again and users think logout failed. `BasicModeBrowserTest` uses Playwright `httpCredentials` (a new context, `@TestHTTPResource` for the base URL), which bypasses the browser's own credential cache. `BasicModeRealBrowserTest` drives Chromium and Firefox (WebKit only on macOS, where it uses Safari's network stack) with `user:pass@` URLs to exercise the real cache. Firefox needs the `network.http.phishy-userpass-length` pref for that, and reports a cancelled dialog as `NS_ERROR_NET_EMPTY_RESPONSE`.
- **OIDC roles** come from the access token (`quarkus.oidc.roles.source=accesstoken`, `realm_access/roles`). Users and roles in `quarkus-faces-realm.json` must stay in sync with `application-form.properties`, `application-basic.properties` and `DemoUsers`.
- `quarkus-jackson` is required in the `oidc` profile. Without it OIDC fails with `NoClassDefFoundError: com/fasterxml/jackson/...` when it parses Keycloak metadata.

## Conventions

- Classes bound in EL must be POJOs with getters/setters, not records (EL can't read derived `isX()` on records).
- Native image and EL: a non-CDI POJO read from EL needs `@RegisterForReflection` (see `DemoUser`). `#{list.size()}` on JDK immutable collections and `#{request.userPrincipal.name}` work in native only from MyFaces 4.1.5 (MYFACES-4779). The native ITs on `/secure/admin.xhtml` and `/secure/user.xhtml` cover them.
- The `native` Maven profile must stay **last** in the pom. It overrides `quarkus.profile` with `${app.profile}` so the image is built with `form`/`basic`/`oidc`/`prod`, not `dev,...`, which would bake in Development stage.
- Browser tests use Quarkiverse `quarkus-playwright` (`@WithPlaywright`, `@InjectPlaywright BrowserContext`, relative URLs), not HtmlUnit. HTTP-only checks use RestAssured.
- UI: one shared template `WEB-INF/template.xhtml`. Styling is in `resources/demo/css/app.css` and is built on PrimeFaces theme CSS variables so `saga-blue` (light) and `arya-blue` (dark, from `primefaces-themes`) both work. PrimeFlex comes from mvnpm (`library="_static"`). `ThemeBean.toggle()` must post-redirect-get, or the POST response renders with the old theme.
