package com.melloware.faces.security;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Plain Servlet API view of the logged-in user, independent of Faces and CDI.
 * <p>
 * Proves that Quarkus propagates its {@code SecurityIdentity} into the servlet container, so
 * {@link HttpServletRequest#getUserPrincipal()} and {@link HttpServletRequest#isUserInRole(String)}
 * return the right values in BOTH the form login and OIDC modes. Lives under {@code /secure/}
 * so it requires authentication like every other secure page.
 */
@WebServlet(urlPatterns = "/secure/whoami")
public class WhoAmIServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Principal principal = request.getUserPrincipal();
        response.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("principal=" + (principal == null ? "null" : principal.getName()));
            out.println("remoteUser=" + request.getRemoteUser());
            out.println("isUserInRole(admin)=" + request.isUserInRole("admin"));
            out.println("isUserInRole(user)=" + request.isUserInRole("user"));
        }
    }
}
