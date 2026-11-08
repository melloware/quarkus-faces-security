package com.melloware.faces.security.form;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.time.Duration;

import org.jboss.logging.Logger;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.VertxHttpConfig;
import io.quarkus.vertx.http.runtime.security.PersistentLoginManager;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * PROGRAMMATIC form login from a Faces action method (the question from quarkus-faces#409).
 * <p>
 * Quarkus has no servlet {@code request.login()}. Instead of POSTing to {@code /j_security_check},
 * this bean does what {@code j_security_check} does internally:
 * <ol>
 * <li>Verify the credentials with {@link IdentityProviderManager} - the same identity providers
 * (here the embedded users) that form authentication uses.</li>
 * <li>Issue the same encrypted {@code quarkus-credential} cookie with Quarkus'
 * {@link PersistentLoginManager}, configured from the same {@code quarkus.http.auth.form.*} and
 * {@code quarkus.http.auth.session.encryption-key} settings, so every later request is authenticated
 * by the normal form mechanism (and logout works the same way).</li>
 * <li>Redirect to the page originally requested (Quarkus' {@code quarkus-redirect-location} cookie),
 * or to the landing page.</li>
 * </ol>
 * Because it's a normal Faces action you get Faces validation, {@code p:messages}, ajax and a place
 * to add your own checks (captcha, lockout, auditing) before the user is logged in.
 * <p>
 * NOTE: {@link PersistentLoginManager} is a public class in a Quarkus {@code runtime} package, not a
 * documented API, so re-check it when upgrading Quarkus. The demo's browser tests cover this flow.
 */
@Named
@RequestScoped
public class LoginController implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LoginController.class);

    @Inject
    IdentityProviderManager identityProviderManager;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** Faces action: authenticate, set the form-auth cookie, redirect. */
    public void login() throws IOException {
        FacesContext faces = FacesContext.getCurrentInstance();
        SecurityIdentity identity;
        try {
            identity = identityProviderManager.authenticateBlocking(
                    new UsernamePasswordAuthenticationRequest(username, new PasswordCredential(password.toCharArray())));
        } catch (AuthenticationFailedException e) {
            LOG.infof("Programmatic login failed for [%s]", username);
            faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Invalid username or password.", "Please try again."));
            faces.validationFailed();
            return;
        } finally {
            password = null;
        }

        RoutingContext rc = currentVertxRequest.getCurrent();
        VertxHttpConfig http = httpConfig();
        FormAuthConfig form = http.auth().form();

        // Same cookie j_security_check would have created.
        loginManager(http, form).save(identity, rc, null, rc.request().isSSL());
        LOG.infof("Programmatic login succeeded for [%s] with roles %s", identity.getPrincipal().getName(),
                identity.getRoles());

        ExternalContext external = faces.getExternalContext();
        external.redirect(external.getRequestContextPath() + redirectTarget(rc, form));
        faces.responseComplete();
    }

    /** Page originally requested (saved by Quarkus before redirecting to the login page), else the landing page. */
    private String redirectTarget(RoutingContext rc, FormAuthConfig form) {
        Cookie location = rc.request().getCookie(form.locationCookie());
        if (location != null) {
            rc.response().addCookie(Cookie.cookie(form.locationCookie(), "").setPath("/").setMaxAge(0));
            try {
                // Keep only path + query so the cookie can never redirect to another host.
                URI uri = URI.create(location.getValue());
                if (uri.getRawPath() != null && uri.getRawPath().startsWith("/")) {
                    return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to the landing page
            }
        }
        return form.landingPage().orElse("/index.xhtml");
    }

    private static VertxHttpConfig httpConfig() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(VertxHttpConfig.class);
    }

    /** Built exactly like FormAuthenticationMechanism builds its own manager. */
    private static PersistentLoginManager loginManager(VertxHttpConfig http, FormAuthConfig form) {
        String key = http.encryptionKey().orElseThrow(() -> new IllegalStateException(
                "Programmatic login needs quarkus.http.auth.session.encryption-key so the cookie can be read back"));
        return new PersistentLoginManager(key, form.cookieName(), form.timeout().toMillis(),
                form.newCookieInterval().toMillis(), form.httpOnlyCookie(), form.cookieSameSite().name(),
                form.cookiePath().orElse(null), form.cookieMaxAge().map(Duration::toSeconds).orElse(-1L),
                form.cookieDomain().orElse(null));
    }
}
