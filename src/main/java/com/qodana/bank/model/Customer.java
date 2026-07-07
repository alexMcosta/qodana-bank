package com.qodana.bank.model;

public class Customer extends User {
    private double checkingBalance;
    private double savingsBalance;

    public Customer(String username, String password, double checking, double savings) {
        super(username, password, false);
        this.checkingBalance = checking;
        this.savingsBalance = savings;
    }

    public double getCheckingBalance() { return checkingBalance; }
    public double getSavingsBalance() { return savingsBalance; }

    public boolean transferToSavings(double amount) {
        if (amount > 0 && amount <= checkingBalance) {
            checkingBalance -= amount;
            savingsBalance += amount;
            return true;
        }
        return false;
    }

    public boolean transferToChecking(double amount) {
        if (amount > 0 && amount <= savingsBalance) {
            savingsBalance -= amount;
            checkingBalance += amount;
            return true;
        }
        return false;
    }
}
