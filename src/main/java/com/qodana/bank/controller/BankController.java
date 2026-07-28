package com.qodana.bank.controller;

import com.qodana.bank.model.*;
import com.qodana.bank.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BankController {

    @Autowired
    private BankService bankService;

    private User getAuthenticatedUser(HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return null;
        return bankService.getUserByUsername(username);
    }

    private Customer getAuthenticatedCustomer(HttpSession session) {
        User user = getAuthenticatedUser(session);
        return (user instanceof Customer) ? (Customer) user : null;
    }

    private User getAuthenticatedAdmin(HttpSession session) {
        User user = getAuthenticatedUser(session);
        return (user != null && user.isAdmin()) ? user : null;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpSession session) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        User user = bankService.authenticate(username, password);
        if (user != null) {
            session.setAttribute("user", username);
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(user);
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> getAccounts(HttpSession session) {
        Customer customer = getAuthenticatedCustomer(session);
        if (customer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        return ResponseEntity.ok(customer.getAccounts());
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@RequestBody Map<String, String> request, HttpSession session) {
        Customer customer = getAuthenticatedCustomer(session);
        if (customer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String type = request.getOrDefault("type", "CHECKING");
        String nickname = request.getOrDefault("nickname", type);
        
        Account newAcc = new Account(type, nickname, 0.0);
        customer.addAccount(newAcc);
        
        return ResponseEntity.ok(newAcc);
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request, HttpSession session) {
        Customer customer = getAuthenticatedCustomer(session);
        if (customer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String fromAccNum = (String) request.get("fromAccount");
        String toAccNum = (String) request.get("toAccount");
        double amount = Double.parseDouble(request.get("amount").toString());

        Account fromAcc = customer.getAccountByNumber(fromAccNum);
        if (fromAcc == null) return ResponseEntity.badRequest().body("Source account not found");

        // Risk Check
        RiskEvaluation eval = bankService.getRiskEngine().evaluate(customer, "TRANSFER", fromAcc, amount, toAccNum, bankService.getTransactionsForUser(customer.getUsername()));
        if (eval.getLevel() == RiskLevel.BLOCK) {
            bankService.addTransaction(new Transaction(customer.getUsername(), "TRANSFER_OUT", fromAcc.getNickname(), amount, fromAcc.getBalance(), TransactionStatus.BLOCKED));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Transaction blocked: " + eval.getReason());
        }
        
        if (eval.getLevel() == RiskLevel.REVIEW) {
            bankService.addTransaction(new Transaction(customer.getUsername(), "TRANSFER_OUT", fromAcc.getNickname(), amount, fromAcc.getBalance(), TransactionStatus.UNDER_REVIEW));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("Transaction under review: " + eval.getReason());
        }

        if (eval.getLevel() == RiskLevel.REQUIRE_2FA) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body("2FA required: " + eval.getReason());
        }
        
        boolean success = customer.transfer(fromAccNum, toAccNum, amount);
        
        if (success) {
            Account from = customer.getAccountByNumber(fromAccNum);
            Account to = customer.getAccountByNumber(toAccNum);
            bankService.addTransaction(new Transaction(customer.getUsername(), "TRANSFER_OUT", from.getNickname(), amount, 
                from.getBalance(), TransactionStatus.COMPLETED));
            bankService.addTransaction(new Transaction(customer.getUsername(), "TRANSFER_IN", to.getNickname(), amount, 
                to.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        }
        
        bankService.addTransaction(new Transaction(customer.getUsername(), "TRANSFER_FAILED", fromAccNum, amount, 
            0, TransactionStatus.FAILED));
        return ResponseEntity.badRequest().body("Insufficient funds or invalid accounts");
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> request, HttpSession session) {
        Customer customer = getAuthenticatedCustomer(session);
        if (customer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String accountNumber = (String) request.get("accountNumber");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success = customer.deposit(accountNumber, amount);
        if (success) {
            Account acc = customer.getAccountByNumber(accountNumber);
            bankService.addTransaction(new Transaction(customer.getUsername(), "DEPOSIT", acc.getNickname(), amount, acc.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Deposit failed");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> request, HttpSession session) {
        Customer customer = getAuthenticatedCustomer(session);
        if (customer == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String accountNumber = (String) request.get("accountNumber");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success = customer.withdraw(accountNumber, amount);
        
        if (success) {
            Account acc = customer.getAccountByNumber(accountNumber);
            bankService.addTransaction(new Transaction(customer.getUsername(), "WITHDRAWAL", acc.getNickname(), amount, acc.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        } else {
            bankService.addTransaction(new Transaction(customer.getUsername(), "WITHDRAWAL_FAILED", accountNumber, amount, 0, TransactionStatus.FAILED));
            return ResponseEntity.badRequest().body("Insufficient funds");
        }
    }

    @PostMapping("/admin/adjust")
    public ResponseEntity<?> adjustBalance(@RequestBody Map<String, Object> request, HttpSession session) {
        User admin = getAuthenticatedAdmin(session);
        if (admin == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        
        String targetUser = (String) request.get("username");
        String accountNumber = (String) request.get("accountNumber");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        User user = bankService.getUserByUsername(targetUser);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Target not a customer");
        
        Customer customer = (Customer) user;
        Account acc = customer.getAccountByNumber(accountNumber);
        if (acc == null) return ResponseEntity.badRequest().body("Account not found");
        
        acc.setBalance(amount);
        
        bankService.addTransaction(new Transaction(targetUser, "ADMIN_ADJUSTMENT", acc.getNickname(), amount, amount, TransactionStatus.COMPLETED));
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@RequestParam(required = false) String type, 
                                            @RequestParam(required = false) String account,
                                            @RequestParam(required = false) String targetUser,
                                            HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        List<Transaction> txs;
        
        if (user.isAdmin()) {
            if (targetUser != null && !targetUser.isEmpty()) {
                txs = bankService.getTransactionsForUser(targetUser);
            } else {
                txs = new java.util.ArrayList<>(bankService.getAllTransactions());
            }
        } else {
            txs = bankService.getTransactionsForUser(user.getUsername());
        }
        
        // Basic filtering
        if (type != null && !type.isEmpty()) {
            txs.removeIf(t -> !t.getType().equalsIgnoreCase(type));
        }
        if (account != null && !account.isEmpty()) {
            txs.removeIf(t -> !t.getAccount().equalsIgnoreCase(account));
        }
        
        return ResponseEntity.ok(txs);
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<String> exportTransactions(@RequestParam(required = false) String targetUser,
                                                     HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        List<Transaction> txs;
        
        if (user.isAdmin() && targetUser != null && !targetUser.isEmpty()) {
            txs = bankService.getTransactionsForUser(targetUser);
        } else {
            txs = bankService.getTransactionsForUser(user.getUsername());
        }
        
        StringBuilder csv = new StringBuilder("Timestamp,Type,Account,Amount,BalanceAfter,Status\n");
        for (Transaction t : txs) {
            csv.append(t.toString()).append("\n");
        }
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=transactions.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getMessages(HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        if (user.isAdmin()) {
            return ResponseEntity.ok(bankService.getAllMessages());
        } else {
            return ResponseEntity.ok(bankService.getMessagesForUser(user.getUsername()));
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> request, HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String receiver = request.get("receiver");
        String content = request.get("content");
        
        bankService.addMessage(new Message(user.getUsername(), receiver, content));
        return ResponseEntity.ok().build();
    }

    // Risk Management Endpoints
    @PostMapping("/admin/risk/block")
    public ResponseEntity<?> blockRecipient(@RequestBody Map<String, String> request, HttpSession session) {
        if (getAuthenticatedAdmin(session) == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        String accountNumber = request.get("accountNumber");
        bankService.getRiskEngine().blockRecipient(accountNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/risk/unblock")
    public ResponseEntity<?> unblockRecipient(@RequestBody Map<String, String> request, HttpSession session) {
        if (getAuthenticatedAdmin(session) == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        String accountNumber = request.get("accountNumber");
        bankService.getRiskEngine().unblockRecipient(accountNumber);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/risk/blocked")
    public ResponseEntity<?> getBlockedRecipients(HttpSession session) {
        if (getAuthenticatedAdmin(session) == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return ResponseEntity.ok(bankService.getRiskEngine().getBlockedRecipients());
    }

    @PostMapping("/admin/risk/review")
    public ResponseEntity<?> reviewTransaction(@RequestBody Map<String, String> request, HttpSession session) {
        if (getAuthenticatedAdmin(session) == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        String transactionId = request.get("transactionId");
        String action = request.get("action"); // APPROVE, REJECT

        List<Transaction> transactions = bankService.getAllTransactions();
        for (Transaction t : transactions) {
            if (t.getId().equals(transactionId) && t.getStatus() == TransactionStatus.UNDER_REVIEW) {
                if ("APPROVE".equals(action)) {
                    try {
                        java.lang.reflect.Field statusField = Transaction.class.getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(t, TransactionStatus.COMPLETED);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                } else {
                    try {
                        java.lang.reflect.Field statusField = Transaction.class.getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(t, TransactionStatus.REVERSED);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                }
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.notFound().build();
    }
}
