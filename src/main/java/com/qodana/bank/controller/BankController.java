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
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(bankService.getUserByUsername(username));
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> getAccounts(HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        return ResponseEntity.ok(((Customer) user).getAccounts());
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@RequestBody Map<String, String> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        String type = request.getOrDefault("type", "CHECKING");
        String nickname = request.getOrDefault("nickname", type);
        
        Account newAcc = new Account(type, nickname, 0.0);
        ((Customer) user).addAccount(newAcc);
        
        return ResponseEntity.ok(newAcc);
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        Customer customer = (Customer) user;
        String fromAccNum = (String) request.get("fromAccount");
        String toAccNum = (String) request.get("toAccount");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success = customer.transfer(fromAccNum, toAccNum, amount);
        
        if (success) {
            Account from = customer.getAccountByNumber(fromAccNum);
            Account to = customer.getAccountByNumber(toAccNum);
            bankService.addTransaction(new Transaction(username, "TRANSFER_OUT", from.getNickname(), amount, 
                from.getBalance(), TransactionStatus.COMPLETED));
            bankService.addTransaction(new Transaction(username, "TRANSFER_IN", to.getNickname(), amount, 
                to.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        }
        
        bankService.addTransaction(new Transaction(username, "TRANSFER_FAILED", fromAccNum, amount, 
            0, TransactionStatus.FAILED));
        return ResponseEntity.badRequest().body("Insufficient funds or invalid accounts");
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        Customer customer = (Customer) user;
        String accountNumber = (String) request.get("accountNumber");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success = customer.deposit(accountNumber, amount);
        if (success) {
            Account acc = customer.getAccountByNumber(accountNumber);
            bankService.addTransaction(new Transaction(username, "DEPOSIT", acc.getNickname(), amount, acc.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Deposit failed");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        Customer customer = (Customer) user;
        String accountNumber = (String) request.get("accountNumber");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success = customer.withdraw(accountNumber, amount);
        
        if (success) {
            Account acc = customer.getAccountByNumber(accountNumber);
            bankService.addTransaction(new Transaction(username, "WITHDRAWAL", acc.getNickname(), amount, acc.getBalance(), TransactionStatus.COMPLETED));
            return ResponseEntity.ok().build();
        } else {
            bankService.addTransaction(new Transaction(username, "WITHDRAWAL_FAILED", accountNumber, amount, 0, TransactionStatus.FAILED));
            return ResponseEntity.badRequest().body("Insufficient funds");
        }
    }

    @PostMapping("/admin/adjust")
    public ResponseEntity<?> adjustBalance(@RequestBody Map<String, Object> request, HttpSession session) {
        String adminUsername = (String) session.getAttribute("user");
        if (adminUsername == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User admin = bankService.getUserByUsername(adminUsername);
        if (admin == null || !admin.isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        
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
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        List<Transaction> txs;
        
        if (user.isAdmin()) {
            if (targetUser != null && !targetUser.isEmpty()) {
                txs = bankService.getTransactionsForUser(targetUser);
            } else {
                txs = new java.util.ArrayList<>(bankService.getAllTransactions());
            }
        } else {
            txs = bankService.getTransactionsForUser(username);
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
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        List<Transaction> txs;
        
        if (user.isAdmin() && targetUser != null && !targetUser.isEmpty()) {
            txs = bankService.getTransactionsForUser(targetUser);
        } else {
            txs = bankService.getTransactionsForUser(username);
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
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (user.isAdmin()) {
            return ResponseEntity.ok(bankService.getAllMessages());
        } else {
            return ResponseEntity.ok(bankService.getMessagesForUser(username));
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        String receiver = request.get("receiver");
        String content = request.get("content");
        
        bankService.addMessage(new Message(username, receiver, content));
        return ResponseEntity.ok().build();
    }
}
