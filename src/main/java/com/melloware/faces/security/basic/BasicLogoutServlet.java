package com.melloware.faces.security.basic;

import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.microprofile.config.ConfigProvider;

import com.melloware.faces.security.SecurityService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Logout" for HTTP Basic authentication ({@code mvn -Pbasic}).
 * <p>
 * Basic auth has no logout: the browser caches the username and password and resends them on every
 * request until it is closed. This public page makes it forget them <b>without</b> showing the login
 * dialog again:
 * <ol>
 * <li>A script sends an {@code XMLHttpRequest} to the protected {@link BasicLoginServlet#PATH} with
 * made-up credentials. Credentials passed to {@code XMLHttpRequest.open()} replace the ones the
 * browser has cached for the realm, and because the script supplied them, the {@code 401} that comes
 * back goes to the script instead of opening the browser's dialog.</li>
 * <li>The script then goes to the home page, which is now requested without credentials.</li>
 * </ol>
 * The usual alternative, answering a page request with a {@code 401} + challenge, makes the browser
 * show its dialog again, and users read that as "logout didn't work".
 */
@WebServlet(urlPatterns = BasicLogoutServlet.PATH)
public class BasicLogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String PATH = "/basic-logout";

    /** Username of the made-up credentials; never a real user. */
    public static final String LOGOUT_USER = "logout";

    static boolean isBasicMode() {
        return SecurityService.MODE_BASIC.equals(ConfigProvider.getConfig()
                .getOptionalValue("demo.security.mode", String.class).orElse(SecurityService.MODE_NONE));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isBasicMode()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contextPath = request.getContextPath();
        String home = contextPath + "/index.xhtml?logout=true";
        String probe = BasicLoginServlet.loginUrl(contextPath);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<title>Signing out</title></head><body id=\"basic-logout\">");
            out.println("<p>Signing out... <a href=\"" + home + "\">Back to home</a></p>");
            out.println("<script>");
            out.println("(function () {");
            out.println("  var done = function () { window.location.replace('" + home + "'); };");
            out.println("  try {");
            out.println("    var xhr = new XMLHttpRequest();");
            out.println("    xhr.open('GET', '" + probe + "', true, '" + LOGOUT_USER + "', 'logout-' + Date.now());");
            out.println("    xhr.onloadend = done;");
            out.println("    xhr.send();");
            out.println("  } catch (e) { done(); }");
            out.println("})();");
            out.println("</script></body></html>");
        }
    }
}
