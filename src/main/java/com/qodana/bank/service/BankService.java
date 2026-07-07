package com.qodana.bank.service;

import com.qodana.bank.model.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class BankService {
    private Map<String, User> users = new HashMap<>();
    private List<Message> messages = new ArrayList<>();

    @PostConstruct
    public void seedData() {
        users.put("alice", new Customer("alice", "pass1", 1000.0, 5000.0));
        users.put("bob", new Customer("bob", "pass2", 500.0, 200.0));
        users.put("admin", new Admin("admin", "admin123"));
    }

    public void addUser(User user) {
        users.put(user.getUsername(), user);
    }

    public User authenticate(String username, String password) {
        User u = users.get(username);
        if (u != null && u.authenticate(password)) return u;
        return null;
    }

    public void addMessage(Message m) { messages.add(m); }
    
    public List<Message> getAllMessages() { return messages; }
    
    public List<Message> getMessagesForUser(String username) {
        List<Message> userMsgs = new ArrayList<>();
        for (Message m : messages) {
            if (m.getSender().equals(username) || m.getReceiver().equals(username)) {
                userMsgs.add(m);
            }
        }
        return userMsgs;
    }

    public User getUserByUsername(String username) {
        return users.get(username);
    }
}
