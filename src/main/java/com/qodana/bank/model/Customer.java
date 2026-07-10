package com.qodana.bank.model;

import java.util.ArrayList;
import java.util.List;

public class Customer extends User {
    private List<Account> accounts = new ArrayList<>();

    public Customer(String username, String password) {
        super(username, password, false);
    }

    public List<Account> getAccounts() { return accounts; }

    public void addAccount(Account account) {
        accounts.add(account);
    }

    public Account getAccountByNumber(String accountNumber) {
        for (Account acc : accounts) {
            if (acc.getAccountNumber().equals(accountNumber)) {
                return acc;
            }
        }
        return null;
    }

    // Compatibility methods for existing logic (finding first of type)
    public Account getPrimaryAccount(String type) {
        for (Account acc : accounts) {
            if (acc.getType().equalsIgnoreCase(type)) {
                return acc;
            }
        }
        return null;
    }

    public double getCheckingBalance() {
        Account acc = getPrimaryAccount("CHECKING");
        return acc != null ? acc.getBalance() : 0.0;
    }

    public double getSavingsBalance() {
        Account acc = getPrimaryAccount("SAVINGS");
        return acc != null ? acc.getBalance() : 0.0;
    }

    public boolean transfer(String fromAccountNumber, String toAccountNumber, double amount) {
        Account from = getAccountByNumber(fromAccountNumber);
        Account to = getAccountByNumber(toAccountNumber);
        if (from != null && to != null && from.withdraw(amount)) {
            if (to.deposit(amount)) {
                return true;
            } else {
                // Rollback
                from.deposit(amount);
            }
        }
        return false;
    }

    public boolean deposit(String accountNumber, double amount) {
        Account acc = getAccountByNumber(accountNumber);
        return acc != null && acc.deposit(amount);
    }

    public boolean withdraw(String accountNumber, double amount) {
        Account acc = getAccountByNumber(accountNumber);
        return acc != null && acc.withdraw(amount);
    }
}
