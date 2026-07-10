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
    void testAccountLogic() {
        Account acc = new Account("CHECKING", "My Checking", 100.0);
        assertTrue(acc.deposit(50.0));
        assertEquals(150.0, acc.getBalance());
        assertTrue(acc.withdraw(30.0));
        assertEquals(120.0, acc.getBalance());
        assertFalse(acc.withdraw(200.0));
    }

    @Test
    void testCustomerMultiAccount() {
        Customer alice = new Customer("alice", "pass1");
        Account ch = new Account("CH1", "CHECKING", "Checking", 100.0);
        Account sa = new Account("SA1", "SAVINGS", "Savings", 100.0);
        alice.addAccount(ch);
        alice.addAccount(sa);

        assertTrue(alice.transfer("CH1", "SA1", 50.0));
        assertEquals(50.0, ch.getBalance());
        assertEquals(150.0, sa.getBalance());
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
        request.put("fromAccount", "ACC-ALICE-CH");
        request.put("toAccount", "ACC-ALICE-SA");
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

    @Test
    void testTransactions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "alice");

        // 1. Deposit
        Map<String, Object> depositReq = new HashMap<>();
        depositReq.put("account", "CHECKING");
        depositReq.put("amount", 200.0);
        mockMvc.perform(post("/api/deposit").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isOk());

        // 2. Withdraw
        Map<String, Object> withdrawReq = new HashMap<>();
        withdrawReq.put("account", "CHECKING");
        withdrawReq.put("amount", 50.0);
        mockMvc.perform(post("/api/withdraw").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isOk());

        // 3. Transfer
        Map<String, Object> transferReq = new HashMap<>();
        transferReq.put("direction", "toSavings");
        transferReq.put("amount", 100.0);
        mockMvc.perform(post("/api/transfer").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk());

        // 4. Check transactions
        mockMvc.perform(get("/api/transactions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4)))) // Deposit(1) + Withdrawal(1) + Transfer(2 txs)
                .andExpect(jsonPath("$[?(@.type=='DEPOSIT')].amount", contains(200.0)))
                .andExpect(jsonPath("$[?(@.type=='WITHDRAWAL')].amount", contains(50.0)))
                .andExpect(jsonPath("$[?(@.type=='TRANSFER_OUT')].amount", contains(100.0)));
        
        // 5. Admin adjustment
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("user", "admin");
        Map<String, Object> adjustReq = new HashMap<>();
        adjustReq.put("username", "alice");
        adjustReq.put("account", "SAVINGS");
        adjustReq.put("amount", 9999.0);
        mockMvc.perform(post("/api/admin/adjust").session(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustReq)))
                .andExpect(status().isOk());
        
        // Verify alice's balance and transaction
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsBalance", is(9999.0)));
    }
}
