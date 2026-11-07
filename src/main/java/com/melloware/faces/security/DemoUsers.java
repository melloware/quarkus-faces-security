package com.melloware.faces.security;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * The demo users, for display only. The real definitions live in
 * application-form.properties (form), application-basic.properties (basic) and
 * quarkus-faces-realm.json (oidc).
 */
@Named
@ApplicationScoped
public class DemoUsers {

    private final List<DemoUser> users = List.of(
            new DemoUser("alice", "alice", "Alice Admin", List.of("admin", "user"),
                    "Can open every page, including the admin console."),
            new DemoUser("bob", "bob", "Bob User", List.of("user"),
                    "Can open the user page; the admin console is denied."));

    public List<DemoUser> getUsers() {
        return users;
    }
}
