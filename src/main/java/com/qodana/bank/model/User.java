package main.java.com.qodana.bank.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class User {
    private final String username;
    @JsonIgnore
    private final String password;
    private final boolean isAdmin;

    public User(String username, String password, boolean isAdmin) {
        this.username = username;
        this.password = password;
        this.isAdmin = isAdmin;
    }

    public String getUsername() { return username; }
    public boolean authenticate(String pass) { return password.equals(pass); }
    public boolean isAdmin() { return isAdmin; }
}
