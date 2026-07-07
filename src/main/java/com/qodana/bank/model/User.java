package com.qodana.bank.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class User {
    private String username;
    @JsonIgnore
    private String password;
    private boolean isAdmin;

    public User(String username, String password, boolean isAdmin) {
        this.username = username;
        this.password = password;
        this.isAdmin = isAdmin;
    }

    public String getUsername() { return username; }
    public boolean authenticate(String pass) { return password.equals(pass); }
    public boolean isAdmin() { return isAdmin; }
}
