package com.qodana.bank.model;

import java.time.LocalDateTime;

public class Transaction {
    private String id;
    private String username;
    private String type; // TRANSFER, DEPOSIT, WITHDRAWAL, ADJUSTMENT
    private String account; // CHECKING, SAVINGS
    private double amount;
    private double balanceAfter;
    private LocalDateTime timestamp;
    private TransactionStatus status;

    public Transaction(String username, String type, String account, double amount, double balanceAfter, TransactionStatus status) {
        this.id = java.util.UUID.randomUUID().toString();
        this.username = username;
        this.type = type;
        this.account = account;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = LocalDateTime.now();
        this.status = status;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getType() { return type; }
    public String getAccount() { return account; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public TransactionStatus getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("%s, %s, %s, %.2f, %.2f, %s, %s",
                timestamp, type, account, amount, balanceAfter, status, username);
    }
}
