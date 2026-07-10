package com.qodana.bank.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class LoggingService {
    private static final Logger logger = LogManager.getLogger(LoggingService.class);

    public void logSecurityEvent(String event) {
        logger.warn("SECURITY EVENT: " + event);
    }

    public void logTransaction(String username, String type, double amount) {
        logger.info("TRANSACTION: User {} performed {} of amount {}", username, type, amount);
    }

    public void logError(String message, Throwable t) {
        logger.error(message, t);
    }
}
