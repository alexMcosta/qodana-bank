package com.qodana.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qodana.bank.model.*;
import com.qodana.bank.service.BankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BankApplicationTests {

    @Autowired
    private BankService bankService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        assertNotNull(bankService);
    }

    // --- Model Tests ---

    @Test
    void testCustomerTransferEdges() {
        Customer alice = new Customer("alice", "pass1", 100.0, 100.0);
        assertFalse(alice.transferToSavings(-10.0));
        assertFalse(alice.transferToSavings(200.0));
        assertTrue(alice.transferToSavings(50.0));
        assertEquals(50.0, alice.getCheckingBalance());
        assertEquals(150.0, alice.getSavingsBalance());

        assertFalse(alice.transferToChecking(-10.0));
        assertFalse(alice.transferToChecking(200.0));
        assertTrue(alice.transferToChecking(50.0));
        assertEquals(100.0, alice.getCheckingBalance());
        assertEquals(100.0, alice.getSavingsBalance());
    }

    @Test
    void testUserAuthentication() {
        User admin = new Admin("admin", "admin123");
        assertTrue(admin.authenticate("admin123"));
        assertFalse(admin.authenticate("wrong"));
        assertTrue(admin.isAdmin());
    }

    @Test
    void testMessageGetters() {
        Message m = new Message("alice", "bob", "hello");
        assertEquals("alice", m.getSender());
        assertEquals("bob", m.getReceiver());
        assertEquals("hello", m.getContent());
    }

    // --- Service Tests ---

    @Test
    void testBankService() {
        bankService.seedData(); // Should be called by @PostConstruct, but good to ensure
        assertNotNull(bankService.authenticate("alice", "pass1"));
        assertNull(bankService.authenticate("alice", "wrong"));
        assertNull(bankService.authenticate("nonexistent", "pass"));

        bankService.addMessage(new Message("alice", "Support", "help"));
        List<Message> aliceMsgs = bankService.getMessagesForUser("alice");
        assertFalse(aliceMsgs.isEmpty());
        assertEquals("alice", aliceMsgs.get(0).getSender());

        List<Message> allMsgs = bankService.getAllMessages();
        assertTrue(allMsgs.size() >= aliceMsgs.size());
    }

    // --- Controller Tests ---

    @Test
    void testLoginController() throws Exception {
        Map<String, String> creds = new HashMap<>();
        creds.put("username", "alice");
        creds.put("password", "pass1");

        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")));

        creds.put("password", "wrong");
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testMeController() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "alice");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")));
    }

    @Test
    void testTransferController() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "alice");

        Map<String, Object> request = new HashMap<>();
        request.put("direction", "toSavings");
        request.put("amount", 100.0);

        mockMvc.perform(post("/api/transfer").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Test insufficient funds
        request.put("amount", 10000.0);
        mockMvc.perform(post("/api/transfer").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Test non-customer (admin)
        session.setAttribute("user", "admin");
        mockMvc.perform(post("/api/transfer").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMessagesController() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "alice");

        Map<String, String> msgReq = new HashMap<>();
        msgReq.put("receiver", "Support");
        msgReq.put("content", "I need help");

        mockMvc.perform(post("/api/messages").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Admin view
        session.setAttribute("user", "admin");
        mockMvc.perform(get("/api/messages").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void testLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "alice");
        
        mockMvc.perform(post("/api/logout").session(session))
                .andExpect(status().isOk());
        
        assertTrue(session.isInvalid());
    }
}
