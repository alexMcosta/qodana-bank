package main.java.com.qodana.bank.service;

import com.qodana.bank.model.*;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import java.util.*;
import java.time.LocalDateTime;

@Service
public class RiskEngine {
    private final List<FraudRule> rules = new ArrayList<>();
    private final Set<String> blockedRecipients = new HashSet<>();

    public RiskEngine() {
        // Amount limit rule
        rules.add(new FraudRule() {
            public String getName() { return "Amount limit rule"; }
            public String getReason() { return "high amount"; }
            @Override
            public RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String target, List<Transaction> recent) {
                if (amount > 10000) {
                    return new RiskEvaluation(RiskLevel.REVIEW, "High amount transaction");
                }
                return new RiskEvaluation(RiskLevel.ALLOW, "OK");
            }
        });

        // Too many transfers rule
        rules.add(new FraudRule() {
            public String getName() { return "Too many transfers rule"; }
            public String getReason() { return "frequent transfers"; }
            @Override
            public RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String target, List<Transaction> recent) {
                long count = recent.stream()
                    .filter(t -> t.getType().equals("TRANSFER"))
                    .filter(t -> t.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(5)))
                    .count();
                if (count > 3) {
                    return new RiskEvaluation(RiskLevel.REQUIRE_2FA, "Too many transfers in short time");
                }
                return new RiskEvaluation(RiskLevel.ALLOW, "OK");
            }
        });

        // Blocked recipient rule
        rules.add(new FraudRule() {
            public String getName() { return "Blocked recipient rule"; }
            public String getReason() { return "blocked recipient"; }
            @Override
            public RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String target, List<Transaction> recent) {
                if (target != null && blockedRecipients.contains(target)) {
                    return new RiskEvaluation(RiskLevel.BLOCK, "Recipient is blocked");
                }
                return new RiskEvaluation(RiskLevel.ALLOW, "OK");
            }
        });

        // Large drop in balance
        rules.add(new FraudRule() {
            public String getName() { return "Large drop in balance"; }
            public String getReason() { return "balance drop"; }
            @Override
            public RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String target, List<Transaction> recent) {
                if (amount > account.getBalance() * 0.9) {
                    return new RiskEvaluation(RiskLevel.REVIEW, "Transaction consumes >90% of balance");
                }
                return new RiskEvaluation(RiskLevel.ALLOW, "OK");
            }
        });
    }

    public RiskEvaluation evaluate(Customer customer, String type, Account account, double amount, String targetAccount, List<Transaction> recentTransactions) {
        RiskLevel highestLevel = RiskLevel.ALLOW;
        String reason = "OK";

        for (FraudRule rule : rules) {
            RiskEvaluation eval = rule.evaluate(customer, type, account, amount, targetAccount, recentTransactions);
            if (eval.getLevel().ordinal() > highestLevel.ordinal()) {
                highestLevel = eval.getLevel();
                reason = eval.getReason();
            }
        }

        return new RiskEvaluation(highestLevel, reason);
    }

    public void blockRecipient(String accountNumber) {
        blockedRecipients.add(accountNumber);
    }

    public void unblockRecipient(String accountNumber) {
        blockedRecipients.remove(accountNumber);
    }

    public String exportRulesToJson() {
        return JSON.toJSONString(rules, true);
    }

    public List<Transaction> filterRecentTransactions(List<Transaction> transactions, final String type) {
        List<Transaction> filtered = new ArrayList<>(transactions);
        CollectionUtils.filter(filtered, new Predicate() {
            @Override
            public boolean evaluate(Object object) {
                return ((Transaction) object).getType().equals(type);
            }
        });
        return filtered;
    }

    public Set<String> getBlockedRecipients() {
        return Collections.unmodifiableSet(blockedRecipients);
    }
}
