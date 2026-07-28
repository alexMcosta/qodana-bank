package com.qodana.bank.service;

import com.qodana.bank.model.*;
import java.util.List;

public interface FraudRule {
    RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String targetAccount, List<Transaction> recentTransactions);
}
