package com.melloware.faces.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A demo user, for display only. Plain POJO with getters and setters so Faces EL can bind to it.
 */
public class DemoUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String name;
    private List<String> roles = new ArrayList<>();
    private String description;

    public DemoUser() {
    }

    public DemoUser(String username, String password, String name, List<String> roles, String description) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.roles = new ArrayList<>(roles);
        this.description = description;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Derived property: {@code #{user.admin}} in EL. */
    public boolean isAdmin() {
        return roles != null && roles.contains("admin");
    }
}
