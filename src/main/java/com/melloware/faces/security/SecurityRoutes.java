package com.melloware.faces.security;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.security.ForbiddenException;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Vert.x-level routes that run BEFORE Faces sees the request.
 * <p>
 * Quarkus HTTP permissions are enforced in the Vert.x layer, so a {@code web.xml}
 * {@code <error-page>} never sees their 403s. These handlers turn the raw responses
 * into friendly pages.
 */
@ApplicationScoped
public class SecurityRoutes {

    private static final Logger LOG = Logger.getLogger(SecurityRoutes.class);

    @ConfigProperty(name = "demo.security.mode", defaultValue = SecurityService.MODE_NONE)
    String mode;

    void init(@Observes Router router) {
        if (SecurityService.MODE_NONE.equals(mode)) {
            // Unsecured mode: never serve secure pages, explain why on the home page instead.
            LOG.warn("No security profile selected: /secure/* is blocked. Run with -Pbasic or -Poidc.");
            router.route("/secure/*").order(-1000).handler(rc -> rc.redirect("/index.xhtml?denied=true"));
        }

        // Authenticated but missing a role (e.g. bob on the admin page): show a styled 403 page.
        router.route().order(-1000).failureHandler(rc -> {
            if (rc.failure() instanceof ForbiddenException || rc.statusCode() == 403) {
                if (!rc.response().headWritten()) {
                    rc.redirect("/access-denied.xhtml");
                    return;
                }
            }
            rc.next();
        });
    }
}
