package com.melloware.faces.security;

import java.io.Serializable;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

/** Light / dark theme toggle. Referenced by primefaces.THEME in web.xml. */
@Named
@SessionScoped
public class ThemeBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean dark;

    public String getTheme() {
        return dark ? "arya-blue" : "saga-blue";
    }

    public boolean isDark() {
        return dark;
    }

    /**
     * Flip the theme and redirect back to the same view (post-redirect-get), because PrimeFaces
     * resolves the theme stylesheet before the action runs on the POST itself.
     */
    public String toggle() {
        dark = !dark;
        return FacesContext.getCurrentInstance().getViewRoot().getViewId() + "?faces-redirect=true";
    }
}
