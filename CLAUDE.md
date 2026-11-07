# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A demo Quarkus + Jakarta Faces (MyFaces, PrimeFaces, OmniFaces) app. The same pages, users and roles run unsecured, behind Quarkus form login, or behind OIDC/Keycloak, depending on the Maven profile. See README.md for the full walkthrough. The design spec is in `docs/superpowers/specs/`.

## Commands

```bash
mvn quarkus:dev            # unsecured mode (public page only, /secure/** redirected home)
mvn -Pbasic                # form login + embedded users  (profile defaultGoal = clean quarkus:dev)
mvn -Poidc                 # OIDC via Keycloak Dev Services (needs Docker); defaultGoal = clean compile quarkus:dev
mvn verify                 # unsecured tests only (@Tag("unsecured"))
mvn -Pbasic verify         # form login tests only (@Tag("basic"))
mvn -Poidc verify          # OIDC tests only (@Tag("oidc")) - Docker must be running (fails, not skips, without it)
mvn -Pbasic verify -Dtest='BasicModeBrowserTest#bobHasNoAdminPanelAndIsDeniedAdminConsole'   # single test (needs its profile)
```

Each mode has different JARs on the classpath, so there is no single build that runs every test. Always pass the matching `-P` profile when running a mode's tests.

On Windows, stopping `mvn quarkus:dev` from a background shell can leave the forked dev JVM running and holding `target/*-dev.jar`, which makes the next `clean` fail. Kill the leftover `java.exe` running `quarkus-faces-security-dev.jar`.

## Architecture

- **Each Maven profile brings only its own security JARs.** The base build has only `quarkus-security`. `-Pbasic` adds `quarkus-elytron-security-properties-file`. `-Poidc` adds `quarkus-oidc` and `quarkus-jackson`, plus the source folder `src/main/oidc/java` via build-helper. The profiles also set `<quarkus.profile>dev,basic|oidc</quarkus.profile>`, which layers `application-basic.properties` / `application-oidc.properties` over the base `application.properties` (security off, `demo.security.mode=none`). Don't put `quarkus.oidc.*` or `quarkus.security.users.*` keys in the base file; they'd be unknown keys in the other builds.
- **No OIDC types in `src/main/java`.** Anything that imports `org.eclipse.microprofile.jwt`/`io.quarkus.oidc` goes in `src/main/oidc/java`. `SecurityService` reads claims via the `TokenClaims` interface: `NoTokenClaims` is the `@DefaultBean`, and `OidcTokenClaims` (OIDC folder) replaces it.
- **Tests:** JUnit `@Tag("unsecured"|"basic"|"oidc")` plus surefire `<groups>${test.groups}</groups>`, set by each profile. Each class also uses `QuarkusTestProfile.getConfigProfile()` (`profiles/BasicProfile`, `profiles/OidcProfile`). Every new test class needs both the tag and the matching profile. Keep the profile property files self-contained, because tests load them without the `dev` profile.
- **`SecurityService` (`@Named @RequestScoped`) is the only class that knows the mode.** XHTML must use `#{securityService...}` (`loggedIn`, `hasRole()`, `logout`, ...) and must never branch on the mechanism directly. `logout()` calls `FormAuthenticationMechanism.logout(identity)` in basic mode and redirects to the virtual `quarkus.oidc.logout.path` in oidc mode.
- **Servlet API parity:** `WhoAmIServlet` (`/secure/whoami`) reports `getUserPrincipal()`/`getRemoteUser()`/`isUserInRole()`. Both secured modes have tests asserting these match the Quarkus identity. Under OIDC the principal is `preferred_username`.
- **Authorization happens in the Vert.x HTTP layer** (`quarkus.http.auth.permission.*`, duplicated in both profile files), not in `web.xml`. Faces never sees those 403s, so `SecurityRoutes` (a `@Observes Router` bean) redirects them to `access-denied.xhtml`. In unsecured mode it also redirects `/secure/*` to `/index.xhtml?denied=true`.
- **Form login** is a plain HTML `<form action="j_security_check">` in `login.xhtml`, because Quarkus has no programmatic `request.login()`. Don't convert it to `h:form`.
- **OIDC roles** come from the access token (`quarkus.oidc.roles.source=accesstoken`, `realm_access/roles`). Users and roles in `quarkus-faces-realm.json` must stay in sync with `application-basic.properties` and `DemoUsers`.
- `quarkus-jackson` is required in the `oidc` profile. Without it OIDC fails with `NoClassDefFoundError: com/fasterxml/jackson/...` when it parses Keycloak metadata.

## Conventions

- Classes bound in EL must be POJOs with getters/setters, not records (EL can't read derived `isX()` on records).
- Browser tests use Quarkiverse `quarkus-playwright` (`@WithPlaywright`, `@InjectPlaywright BrowserContext`, relative URLs), not HtmlUnit. HTTP-only checks use RestAssured.
- UI: one shared template `WEB-INF/template.xhtml`. Styling is in `resources/demo/css/app.css` and is built on PrimeFaces theme CSS variables so `saga-blue` (light) and `arya-blue` (dark, from `primefaces-themes`) both work. PrimeFlex comes from mvnpm (`library="_static"`). `ThemeBean.toggle()` must post-redirect-get, or the POST response renders with the old theme.
