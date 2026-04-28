package com.devilsvault.api.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountType;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class TransferQueryTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired TransferRepository transfers;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;
    private String aliceToken;
    private String bobToken;
    private Account aliceChecking;
    private Account aliceSavings;
    private Account bobChecking;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    @BeforeEach
    void setUp() throws Exception {
        transfers.deleteAll();
        accounts.deleteAll();
        users.deleteAll();

        mvc = mvc();

        // Register alice
        MvcResult aliceReg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"tq_alice","email":"tq_alice@example.com","password":"password1","fullName":"Alice Q"}
                            """))
                .andExpect(status().isOk())
                .andReturn();
        aliceToken = mapper.readTree(aliceReg.getResponse().getContentAsString()).get("token").asText();

        // Register bob
        MvcResult bobReg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"tq_bob","email":"tq_bob@example.com","password":"password1","fullName":"Bob Q"}
                            """))
                .andExpect(status().isOk())
                .andReturn();
        bobToken = mapper.readTree(bobReg.getResponse().getContentAsString()).get("token").asText();

        User alice = users.findByUsername("tq_alice").orElseThrow();
        User bob = users.findByUsername("tq_bob").orElseThrow();

        aliceChecking = createAccount(alice, AccountType.CHECKING, "1000.00");
        aliceSavings = createAccount(alice, AccountType.SAVINGS, "500.00");
        bobChecking = createAccount(bob, AccountType.CHECKING, "2000.00");
    }

    private Account createAccount(User owner, AccountType type, String balance) {
        Account a = new Account();
        a.setOwner(owner);
        a.setAccountType(type);
        a.setBalance(new BigDecimal(balance));
        return accounts.save(a);
    }

    private void doTransfer(String token, Long sourceId, Long targetId, String amount) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "sourceAccountId", sourceId,
                "targetAccountId", targetId,
                "amount", amount,
                "description", "test transfer"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void listTransfers_returnsOwnTransfersOnly() throws Exception {
        // Alice transfers to Bob
        doTransfer(aliceToken, aliceChecking.getId(), bobChecking.getId(), "50.00");
        // Bob transfers to Alice
        doTransfer(bobToken, bobChecking.getId(), aliceChecking.getId(), "25.00");

        // Alice should see 2 transfers (one outgoing, one incoming)
        mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        // Bob should also see 2 transfers
        mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listTransfers_crossUserIsolation() throws Exception {
        // Alice internal transfer (alice checking -> alice savings)
        doTransfer(aliceToken, aliceChecking.getId(), aliceSavings.getId(), "100.00");

        // Bob should NOT see Alice's internal transfer
        mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // Alice should see it
        mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTransfers_accountFilter() throws Exception {
        // Alice checking -> alice savings
        doTransfer(aliceToken, aliceChecking.getId(), aliceSavings.getId(), "50.00");
        // Alice checking -> bob checking
        doTransfer(aliceToken, aliceChecking.getId(), bobChecking.getId(), "30.00");

        // Filter by alice savings: should see only the 1 internal transfer
        mvc.perform(get("/api/transfers")
                        .param("accountId", aliceSavings.getId().toString())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Filter by alice checking: should see both
        mvc.perform(get("/api/transfers")
                        .param("accountId", aliceChecking.getId().toString())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listTransfers_accountFilter_forbiddenForOtherUser() throws Exception {
        // Bob tries to filter by alice's account
        mvc.perform(get("/api/transfers")
                        .param("accountId", aliceChecking.getId().toString())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransfers_pagination() throws Exception {
        // Create 5 transfers
        for (int i = 0; i < 5; i++) {
            doTransfer(aliceToken, aliceChecking.getId(), aliceSavings.getId(), "10.00");
        }

        // Page 0, size 2
        mvc.perform(get("/api/transfers")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        // Page 2, size 2 — should have 1 item
        mvc.perform(get("/api/transfers")
                        .param("page", "2")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listTransfers_timeRangeFilter() throws Exception {
        doTransfer(aliceToken, aliceChecking.getId(), aliceSavings.getId(), "10.00");

        // Since in the far future — should return 0
        mvc.perform(get("/api/transfers")
                        .param("since", "2099-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Until in the far past — should return 0
        mvc.perform(get("/api/transfers")
                        .param("until", "2000-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Since in the past, until in the future — should return 1
        mvc.perform(get("/api/transfers")
                        .param("since", "2000-01-01T00:00:00Z")
                        .param("until", "2099-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTransfers_directionIsCorrect() throws Exception {
        // Alice sends to Bob
        doTransfer(aliceToken, aliceChecking.getId(), bobChecking.getId(), "50.00");

        // From Alice's perspective: DEBIT
        MvcResult aliceResult = mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode aliceItems = mapper.readTree(aliceResult.getResponse().getContentAsString()).get("items");
        assertThat(aliceItems.get(0).get("direction").asText()).isEqualTo("DEBIT");

        // From Bob's perspective: CREDIT
        MvcResult bobResult = mvc.perform(get("/api/transfers")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bobItems = mapper.readTree(bobResult.getResponse().getContentAsString()).get("items");
        assertThat(bobItems.get(0).get("direction").asText()).isEqualTo("CREDIT");
    }

    @Test
    void listTransfers_unauthenticatedIsDenied() throws Exception {
        mvc.perform(get("/api/transfers"))
                .andExpect(status().isForbidden());
    }
}
