package com.qodana.bank.model;

import java.util.UUID;

public class Account {
    private final String accountNumber;
    private final String type; // CHECKING, SAVINGS, BUSINESS, etc.
    private String nickname;
    private double balance;
    private double balanceLimit;
    private String status; // ACTIVE, ARCHIVED, CLOSED

    public Account(String type, String nickname, double initialBalance) {
        this.accountNumber = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.type = type;
        this.nickname = nickname;
        this.balance = initialBalance;
        this.balanceLimit = Double.MAX_VALUE;
        this.status = "ACTIVE";
    }

    public Account(String accountNumber, String type, String nickname, double initialBalance) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.nickname = nickname;
        this.balance = initialBalance;
        this.balanceLimit = Double.MAX_VALUE;
        this.status = "ACTIVE";
    }

    public String getAccountNumber() { return accountNumber; }
    public String getType() { return type; }
    public String getNickname() { return nickname; }
    public double getBalance() { return balance; }
    public double getBalanceLimit() { return balanceLimit; }
    public String getStatus() { return status; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setBalanceLimit(double balanceLimit) { this.balanceLimit = balanceLimit; }
    public void setStatus(String status) { this.status = status; }

    public boolean deposit(double amount) {
        if (amount <= 0) return false;
        if (balance + amount > balanceLimit) return false;
        balance += amount;
        return true;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0) return false;
        if (amount > balance) return false;
        balance -= amount;
        return true;
    }

    public void setBalance(double amount) {
        this.balance = amount;
    }
}
