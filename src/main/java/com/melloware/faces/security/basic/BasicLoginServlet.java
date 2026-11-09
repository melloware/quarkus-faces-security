package com.melloware.faces.security.basic;

import java.io.IOException;

import com.melloware.faces.security.SecurityService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Login" for HTTP Basic authentication ({@code mvn -Pbasic}): the target of the topbar Login button.
 * <p>
 * {@code application-basic.properties} protects this path, so an anonymous request gets the
 * {@code 401} challenge and the browser shows its dialog. Once signed in, this just redirects to the
 * user page.
 * <p>
 * <b>Why not challenge on {@code /secure/user.xhtml} directly?</b> A browser only resends cached Basic
 * credentials, without being challenged again, to URLs at or below the <i>directory</i> of the URL
 * that was challenged (RFC 7617, section 2.2). Firefox and Safari follow that strictly. A challenge on
 * {@code /secure/user.xhtml} covers only {@code /secure/}, so public pages such as {@code /index.xhtml}
 * would be requested without credentials and show the user as signed out. This path sits at the
 * root, so the credentials cover the whole app.
 */
@WebServlet(urlPatterns = BasicLoginServlet.PATH)
public class BasicLoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String PATH = "/basic-login";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!BasicLogoutServlet.isBasicMode()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/secure/user.xhtml");
    }

    /** Where the Login button points in basic mode (see {@link SecurityService#getLoginUrl()}). */
    public static String loginUrl(String contextPath) {
        return contextPath + PATH;
    }
}
