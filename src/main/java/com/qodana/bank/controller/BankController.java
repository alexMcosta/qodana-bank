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

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        User user = bankService.getUserByUsername(username);
        if (!(user instanceof Customer)) return ResponseEntity.badRequest().body("Not a customer");
        
        Customer customer = (Customer) user;
        String direction = (String) request.get("direction");
        double amount = Double.parseDouble(request.get("amount").toString());
        
        boolean success;
        if ("toSavings".equals(direction)) {
            success = customer.transferToSavings(amount);
        } else {
            success = customer.transferToChecking(amount);
        }
        
        if (success) return ResponseEntity.ok().build();
        return ResponseEntity.badRequest().body("Insufficient funds or invalid amount");
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
