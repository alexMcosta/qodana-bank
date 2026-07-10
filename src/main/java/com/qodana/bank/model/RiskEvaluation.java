package com.qodana.bank.model;

public class RiskEvaluation {
    private RiskLevel level;
    private String reason;

    public RiskEvaluation(RiskLevel level, String reason) {
        this.level = level;
        this.reason = reason;
    }

    public RiskLevel getLevel() { return level; }
    public String getReason() { return reason; }
}
