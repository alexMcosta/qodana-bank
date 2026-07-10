package main.java.com.qodana.bank.model;

public class Admin extends User {
    public Admin(String username, String password) {
        super(username, password, true);
    }
}
