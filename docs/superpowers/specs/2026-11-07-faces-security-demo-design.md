# Quarkus Faces Security Demo — Design

Date: 2026-11-07
Status: Approved and implemented

## Intent and why

People building Jakarta Faces apps on Quarkus keep asking the same questions (see
[melloware/quarkus-faces#409](https://github.com/melloware/quarkus-faces/issues/409)):
how do I put a real login page in front of my Faces pages, how do I log out, and how
do I move to OIDC/Keycloak later without rewriting my pages? Quarkus does not support
servlet-style programmatic `request.login()`, so the usual Java EE answers don't apply,
and the only working sample in the ticket is a fragile `HttpClient` hack.

This repo is a **small, focused demo app** that answers those questions side by side.
One codebase, one set of pages, one set of users and roles, and three ways to run it:

| Command | Mode | What you get |
|---|---|---|
| `mvn quarkus:dev` | Unsecured | Public page only, with a warning banner. `/secure/**` is denied. |
| `mvn -Pbasic` | Form login | A PrimeFaces login page backed by users in `application-basic.properties`. |
| `mvn -Poidc` | OIDC | Redirect to Keycloak (Quarkus Dev Services), same users and roles via a realm file. |

Success: a developer clones the repo, runs each of the three commands, logs in as
`alice` and `bob`, sees role-based behavior and logout work in both secured modes, and
can copy the pattern into their own app by reading the README.

## Goals

- Demonstrate Quarkus **form-based authentication** (`/j_security_check`) with a proper
  Faces login page, including login error and logout.
- Demonstrate **OIDC authorization-code flow** with Keycloak Dev Services, using the same
  pages, users, and roles.
- Show **role-based access** two ways: path-level (HTTP permissions) and in-page
  (render/hide content by role).
- Pages depend only on a shared `SecurityService` bean, never on which mode is active.
- `@QuarkusTest` coverage for each mode (unsecured, basic, oidc).
- A README walkthrough that explains the whole project and how to run and test it.

## Non-goals

- Production identity storage (database users, password hashing, JPA realm).
- The idle-session countdown dialog / token keep-alive ping from the `oidc-auth` branch.
- Back-channel logout, multi-tenant OIDC, bearer-token REST APIs.
- Native image builds or container images.
- Copying the PrimeFaces Showcase layout wholesale (its menu, template and asset tree).

## Anti-goals (would be a failure even if it "works")

- **Unsecured mode silently exposing secure pages.** `/secure/**` must be denied, and the
  UI must say why.
- **The #409 `HttpClient`-to-`j_security_check` hack** as the login mechanism. The demo
  should show the clean way.
- Pages with `if basic … else oidc …` logic sprinkled through them.
- A demo too big to read in one sitting.

## Constraints

- Follow the profile pattern from the user's `emobility-api` project:
  - `application.properties` disables all security by default (`quarkus.oidc.enabled=false`,
    form auth off, embedded users off).
  - `application-basic.properties` and `application-oidc.properties` turn on their mode.
  - Maven profiles `basic` and `oidc` set `<quarkus.profile>dev,basic</quarkus.profile>` /
    `dev,oidc` and use `<defaultGoal>clean quarkus:dev</defaultGoal>`, so `mvn -Pbasic`
    and `mvn -Poidc` are the whole command.
  - That is the whole pattern. `emobility-api`'s tests don't switch security profiles,
    so test wiring is defined only by this doc's Testing section.
- Stack matches `melloware/quarkus-faces` `main`: Java 21, Quarkus 3.40.1.1,
  MyFaces 4.1.4 (`quarkus-myfaces`), PrimeFaces-Quarkus 4.16.1, OmniFaces-Quarkus 5.5.6.
- OIDC mode and OIDC tests need Docker or Podman (Keycloak Dev Services). Docker is
  available on the dev machine.

## Users and roles (identical in both secured modes)

| User | Password | Roles |
|---|---|---|
| `alice` | `alice` | `admin`, `user` |
| `bob` | `bob` | `user` |

Basic: `quarkus.security.users.embedded.*` with `plain-text=true`.
OIDC: a realm JSON imported by Keycloak Dev Services
(`quarkus.keycloak.devservices.realm-path`) defining the same users and realm roles,
with Quarkus mapping realm roles from the token.

## Pages and behavior

- **`/index.xhtml` (public)**: explains the demo and shows the active mode
  ("Unsecured", "Form login", "OIDC (Keycloak)"). In unsecured mode it shows a warning
  banner: *No security profile selected — only this public page is available. Run with
  `mvn -Pbasic` or `mvn -Poidc`.* Links to the secure pages.
- **`/secure/user.xhtml`**: any authenticated user. Shows username and roles, and an
  admin-only panel rendered only for `admin` (in-page role check).
- **`/secure/admin.xhtml`**: `admin` role only (path-level check). `bob` is refused.
- **`/login.xhtml`** (basic mode): PrimeFaces-styled login form posting to
  `/j_security_check`. Shows an error message after a failed login.
- **Top bar** on every page: shows the logged-in user and a Logout button when signed in,
  or a Login link when not. In unsecured mode it shows neither and the secure links are
  disabled.
- **Logout**: basic mode removes the form-auth cookie
  (`FormAuthenticationMechanism.logout(identity)`) and returns to `/index.xhtml`.
  OIDC mode redirects to the virtual `quarkus.oidc.logout.path` (e.g. `/secure/logout`),
  with `post-logout-path=/index.xhtml`.

## Look and feel

The user wants it to **look nice, not bare-bones**: this is a showcase people will
screenshot and copy. Use the PrimeFaces stack properly:

- A modern built-in PrimeFaces theme (e.g. Saga, with a light/dark toggle if it's cheap),
  PrimeFlex for layout and spacing, PrimeIcons throughout.
- **One shared Facelets template** with a top bar: app name/logo, a colored badge for the
  active mode (Unsecured / Form login / OIDC), and on the right either a Login button or
  a user chip (avatar with initials, username) with a Logout action.
- **Home page:** a hero section explaining the demo, then three cards (one per mode)
  showing the command to run, with the active one highlighted. The unsecured warning
  uses a prominent PrimeFaces warning message, not plain text.
- **Login page:** a centered card on a soft background, with icons in the inputs, a clear
  error message on failure, and a small "demo users" hint (alice / bob).
- **User page:** a profile card (name, roles as colored tags, and auth mode details such
  as token issuer in OIDC), plus the admin-only panel, visibly styled as admin content.
- **Admin page:** something that looks like an admin screen, such as a styled table of the
  demo users and their roles.
- Responsive enough to look right on a laptop and a phone.

## Security wiring

- **Path permissions** (in the secured profile files):
  - `/secure/*` requires `authenticated`.
  - `/secure/admin*` requires role `admin`.
- **Unsecured mode**: a direct hit on `/secure/**` must never serve the page, and must
  explain why. *(Designer's call.)* Redirect it to `/index.xhtml?denied=true`, where the
  banner reads "Secure pages are unavailable because no security profile is selected".
  Do this with a small Vert.x route filter (or an equivalent permission policy) that is
  active only when no security mode is configured. The plain `deny` permission alone is
  not enough, because its default response doesn't say why.
- **`SecurityService`** (`@Named`, request-scoped or application-scoped with injected
  `SecurityIdentity`): `isLoggedIn()`, `getUsername()`, `getRoles()`, `hasRole(String)`,
  `getMode()` (from config), `isSecured()`, and `logout()`, which does the right thing for
  the active mode. This is the only place that knows about modes.
- **Basic mode** form settings: `quarkus.http.auth.form.enabled=true`,
  `login-page=/login.xhtml`, `error-page=/login.xhtml?error=true`,
  `landing-page=/secure/user.xhtml`, HttpOnly cookie, and a fixed
  `quarkus.http.auth.session.encryption-key` (a dev-only value, called out as such).
- **OIDC mode**: `quarkus.oidc.enabled=true`, `application-type=web-app`, client id and
  secret matching the realm file, `restore-path-after-redirect=true`,
  `token-state-manager.split-tokens=true`, `token.refresh-expired=true`, logout path
  as above. Carry over the useful settings from the `oidc-auth` branch wiki; drop the
  Quarkus 3.3 workarounds.

## Maven dependency layout

- Always present: `quarkus-myfaces`, PrimeFaces-Quarkus, OmniFaces-Quarkus, `quarkus-oidc`
  (disabled by config unless `oidc` is active), `quarkus-junit5`, `rest-assured`.
- `quarkus-elytron-security-properties-file`: added by the `basic` Maven profile,
  following `emobility-api`, and also declared at `test` scope in the main dependencies
  so that `mvn verify` can run the basic-mode tests without a profile.

## Testing

**A plain `mvn verify` runs all three modes' tests** (user's decision). Docker is a hard
requirement for the full build. OIDC tests are not skipped when Docker is missing; they
fail. The README states this up front.

`@QuarkusTest` classes, one group per mode, each using a `QuarkusTestProfile` that
selects the matching Quarkus config profile:

- **Unsecured**: `/index.xhtml` returns 200 and contains the warning banner;
  `/secure/user.xhtml` and `/secure/admin.xhtml` are denied.
- **Basic**: anonymous `/secure/user.xhtml` redirects to `/login.xhtml`; posting
  `alice`/`alice` to `/j_security_check` sets the credential cookie, and the user page
  shows `alice` and the admin panel; `bob` can open the user page without the admin
  panel and gets 403 on the admin page; wrong password lands on the error page;
  logout clears the cookie.
- **OIDC**: uses Keycloak Dev Services (Docker required). Anonymous secure access
  redirects to Keycloak; a browser-style login (Quarkiverse Playwright driving the Keycloak form)
  as `alice` and `bob` gives the same role results as basic.

The README explains that the full build needs Docker, and how to run one mode's tests
alone (e.g. `mvn verify -Dtest='Basic*'`).

## README walkthrough

- What the demo is and why it exists (link #409, the wiki, the `oidc-auth` branch).
- Prerequisites (Java 21, Maven, Docker for OIDC).
- The three run commands, and what to click in each mode.
- Users and roles table.
- How it works: the profile and properties-file pattern, path permissions,
  `SecurityService`, form login and logout, OIDC login and logout.
- Running tests (all tests, one mode).
- How to copy the pattern into your own app.

## Left to the builder

- Package name, class names, and exact page layout and styling.
- Exact Keycloak client id, secret, and realm name (must match between realm JSON and
  config).
- Whether `SecurityService` is request- or application-scoped.
- The friendly page for a 403: Quarkus HTTP permissions reject before Faces runs, so a
  `web.xml` `<error-page>` may not catch it. Use a custom handler if it's simple,
  otherwise accept the default response and document it.
- Playwright test details (selectors, waits, browser choice).
