package com.melloware.faces.security;

import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.microprofile.config.ConfigProvider;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Logout" for HTTP Basic authentication ({@code mvn -Pbasic}).
 * <p>
 * Basic auth has no logout: the browser caches the username and password and resends them on every
 * request until it is closed. The usual workaround is to answer a request that carries those cached
 * credentials with a {@code 401} and a fresh {@code WWW-Authenticate} challenge. The browser then
 * treats the credentials as rejected, forgets them and shows its login dialog again. When the user
 * clicks <b>Cancel</b>, the browser renders the body of the 401, which sends them home.
 * <p>
 * {@link SecurityService#logout()} sets a one-time {@value #PENDING_COOKIE} cookie before
 * redirecting here, so only a real logout gets the 401. If the user types their credentials into the
 * dialog again instead of cancelling, the retried request has no cookie and they are simply signed
 * back in. Lives under {@code /secure/} so the browser sends its cached credentials here.
 */
@WebServlet(urlPatterns = BasicLogoutServlet.PATH)
public class BasicLogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String PATH = "/secure/basic-logout";
    public static final String PENDING_COOKIE = "demo-basic-logout";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String mode = ConfigProvider.getConfig().getOptionalValue("demo.security.mode", String.class)
                .orElse(SecurityService.MODE_NONE);
        if (!SecurityService.MODE_BASIC.equals(mode)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contextPath = request.getContextPath();
        if (!hasPendingCookie(request)) {
            // Credentials were re-entered in the browser dialog: they are valid, so just carry on.
            response.sendRedirect(contextPath + "/secure/user.xhtml");
            return;
        }

        Cookie clear = new Cookie(PENDING_COOKIE, "");
        clear.setPath(contextPath + "/secure");
        clear.setMaxAge(0);
        clear.setHttpOnly(true);
        response.addCookie(clear);

        String realm = ConfigProvider.getConfig().getOptionalValue("quarkus.http.auth.realm", String.class)
                .orElse("Quarkus");
        String home = contextPath + "/index.xhtml?logout=true";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<meta http-equiv=\"refresh\" content=\"0;url=" + home + "\">");
            out.println("<title>Signed out</title></head><body id=\"basic-logout\">");
            out.println("<p>You have been signed out. <a href=\"" + home + "\">Back to home</a></p>");
            out.println("</body></html>");
        }
    }

    private static boolean hasPendingCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (PENDING_COOKIE.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
