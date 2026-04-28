package com.devilsvault.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AccountStatementTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    private String registerAndLogin(String username, String email, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "username", username, "email", email, "password", password, "fullName", username));
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void doTransfer(String token, Long sourceId, Long targetId, String amount, String desc) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "sourceAccountId", sourceId, "targetAccountId", targetId,
                "amount", amount, "description", desc));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void getAccountDetail_happyPath() throws Exception {
        String token = registerAndLogin("stmtuser1", "stmtuser1@test.com", "password1");
        User owner = users.findByUsername("stmtuser1").orElseThrow();
        Account acct = new Account();
        acct.setOwner(owner);
        acct.setAccountType(AccountType.CHECKING);
        acct.setBalance(new BigDecimal("1000.00"));
        acct = accounts.save(acct);

        mvc.perform(get("/api/accounts/" + acct.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(acct.getId()))
                .andExpect(jsonPath("$.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.balance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty());
    }

    @Test
    void getAccountDetail_notFound() throws Exception {
        String token = registerAndLogin("stmtuser2", "stmtuser2@test.com", "password1");
        mvc.perform(get("/api/accounts/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAccountDetail_crossUserIsolation() throws Exception {
        String tokenA = registerAndLogin("stmtownerA", "stmtownerA@test.com", "password1");
        String tokenB = registerAndLogin("stmtownerB", "stmtownerB@test.com", "password1");

        User ownerA = users.findByUsername("stmtownerA").orElseThrow();
        Account acctA = new Account();
        acctA.setOwner(ownerA);
        acctA.setAccountType(AccountType.SAVINGS);
        acctA.setBalance(new BigDecimal("5000.00"));
        acctA = accounts.save(acctA);

        mvc.perform(get("/api/accounts/" + acctA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/accounts/" + acctA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void statement_happyPathPagination() throws Exception {
        String token = registerAndLogin("stmtpage1", "stmtpage1@test.com", "password1");
        User owner = users.findByUsername("stmtpage1").orElseThrow();

        Account checking = new Account();
        checking.setOwner(owner);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("10000.00"));
        checking = accounts.save(checking);

        Account savings = new Account();
        savings.setOwner(owner);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("0.00"));
        savings = accounts.save(savings);

        for (int i = 1; i <= 5; i++) {
            doTransfer(token, checking.getId(), savings.getId(), "100.00", "transfer " + i);
        }

        MvcResult result = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("page", "0").param("size", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(3);
        assertThat(body.get("totalElements").asLong()).isEqualTo(5);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("items").size()).isEqualTo(3);

        for (JsonNode item : body.get("items")) {
            assertThat(item.get("direction").asText()).isEqualTo("DEBIT");
            assertThat(item.get("counterpartAccountId").asLong()).isEqualTo(savings.getId());
        }

        MvcResult page2 = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("page", "1").param("size", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body2 = mapper.readTree(page2.getResponse().getContentAsString());
        assertThat(body2.get("items").size()).isEqualTo(2);
    }

    @Test
    void statement_directionFilter() throws Exception {
        String token = registerAndLogin("stmtdir1", "stmtdir1@test.com", "password1");
        User owner = users.findByUsername("stmtdir1").orElseThrow();

        Account checking = new Account();
        checking.setOwner(owner);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("5000.00"));
        checking = accounts.save(checking);

        Account savings = new Account();
        savings.setOwner(owner);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("5000.00"));
        savings = accounts.save(savings);

        doTransfer(token, checking.getId(), savings.getId(), "100.00", "out1");
        doTransfer(token, savings.getId(), checking.getId(), "50.00", "in1");
        doTransfer(token, checking.getId(), savings.getId(), "200.00", "out2");

        MvcResult debitResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("direction", "DEBIT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode debits = mapper.readTree(debitResult.getResponse().getContentAsString());
        assertThat(debits.get("totalElements").asLong()).isEqualTo(2);
        for (JsonNode item : debits.get("items")) {
            assertThat(item.get("direction").asText()).isEqualTo("DEBIT");
        }

        MvcResult creditResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("direction", "CREDIT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode credits = mapper.readTree(creditResult.getResponse().getContentAsString());
        assertThat(credits.get("totalElements").asLong()).isEqualTo(1);
        assertThat(credits.get("items").get(0).get("direction").asText()).isEqualTo("CREDIT");
    }

    @Test
    void statement_runningBalanceWithDirectionFilter() throws Exception {
        String token = registerAndLogin("stmtdirbal1", "stmtdirbal1@test.com", "password1");
        User owner = users.findByUsername("stmtdirbal1").orElseThrow();

        Account checking = new Account();
        checking.setOwner(owner);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("5000.00"));
        checking = accounts.save(checking);

        Account savings = new Account();
        savings.setOwner(owner);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("5000.00"));
        savings = accounts.save(savings);

        // checking: 5000 -> 4900 (DEBIT 100) -> 4950 (CREDIT 50) -> 4750 (DEBIT 200)
        doTransfer(token, checking.getId(), savings.getId(), "100.00", "out1");
        doTransfer(token, savings.getId(), checking.getId(), "50.00", "in1");
        doTransfer(token, checking.getId(), savings.getId(), "200.00", "out2");

        MvcResult debitResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("direction", "DEBIT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode debits = mapper.readTree(debitResult.getResponse().getContentAsString());
        assertThat(debits.get("items").size()).isEqualTo(2);
        // First debit: 5000 - 100 = 4900
        assertThat(new BigDecimal(debits.get("items").get(0).get("runningBalance").asText()))
                .isEqualByComparingTo("4900.00");
        // Second debit: must account for the CREDIT of 50 in between -> 4950 - 200 = 4750
        assertThat(new BigDecimal(debits.get("items").get(1).get("runningBalance").asText()))
                .isEqualByComparingTo("4750.00");

        MvcResult creditResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("direction", "CREDIT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode credits = mapper.readTree(creditResult.getResponse().getContentAsString());
        assertThat(credits.get("items").size()).isEqualTo(1);
        // Credit: 4900 + 50 = 4950
        assertThat(new BigDecimal(credits.get("items").get(0).get("runningBalance").asText()))
                .isEqualByComparingTo("4950.00");
    }

    @Test
    void statement_crossUserIsolation() throws Exception {
        String tokenA = registerAndLogin("stmtiso1", "stmtiso1@test.com", "password1");
        String tokenB = registerAndLogin("stmtiso2", "stmtiso2@test.com", "password1");

        User ownerA = users.findByUsername("stmtiso1").orElseThrow();
        Account acctA = new Account();
        acctA.setOwner(ownerA);
        acctA.setAccountType(AccountType.CHECKING);
        acctA.setBalance(new BigDecimal("1000.00"));
        acctA = accounts.save(acctA);

        mvc.perform(get("/api/accounts/" + acctA.getId() + "/statement")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/accounts/" + acctA.getId() + "/statement")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void statement_runningBalanceCorrectness() throws Exception {
        String token = registerAndLogin("stmtbal1", "stmtbal1@test.com", "password1");
        User owner = users.findByUsername("stmtbal1").orElseThrow();

        Account checking = new Account();
        checking.setOwner(owner);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("1000.00"));
        checking = accounts.save(checking);

        Account savings = new Account();
        savings.setOwner(owner);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("0.00"));
        savings = accounts.save(savings);

        doTransfer(token, checking.getId(), savings.getId(), "100.00", "t1");
        doTransfer(token, checking.getId(), savings.getId(), "200.00", "t2");
        doTransfer(token, savings.getId(), checking.getId(), "50.00", "t3");
        doTransfer(token, checking.getId(), savings.getId(), "150.00", "t4");

        MvcResult result = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page0 = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(page0.get("items").size()).isEqualTo(2);
        assertThat(new BigDecimal(page0.get("items").get(0).get("runningBalance").asText()))
                .isEqualByComparingTo("900.00");
        assertThat(new BigDecimal(page0.get("items").get(1).get("runningBalance").asText()))
                .isEqualByComparingTo("700.00");

        MvcResult result2 = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page1 = mapper.readTree(result2.getResponse().getContentAsString());
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(new BigDecimal(page1.get("items").get(0).get("runningBalance").asText()))
                .isEqualByComparingTo("750.00");
        assertThat(new BigDecimal(page1.get("items").get(1).get("runningBalance").asText()))
                .isEqualByComparingTo("600.00");
    }

    @Test
    void statement_dateFilter() throws Exception {
        String token = registerAndLogin("stmtdate1", "stmtdate1@test.com", "password1");
        User owner = users.findByUsername("stmtdate1").orElseThrow();

        Account checking = new Account();
        checking.setOwner(owner);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("5000.00"));
        checking = accounts.save(checking);

        Account savings = new Account();
        savings.setOwner(owner);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("0.00"));
        savings = accounts.save(savings);

        doTransfer(token, checking.getId(), savings.getId(), "100.00", "date-test");

        MvcResult futureResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("since", "2099-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode futureBody = mapper.readTree(futureResult.getResponse().getContentAsString());
        assertThat(futureBody.get("totalElements").asLong()).isZero();

        MvcResult pastResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .param("until", "2000-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pastBody = mapper.readTree(pastResult.getResponse().getContentAsString());
        assertThat(pastBody.get("totalElements").asLong()).isZero();

        MvcResult allResult = mvc.perform(get("/api/accounts/" + checking.getId() + "/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode allBody = mapper.readTree(allResult.getResponse().getContentAsString());
        assertThat(allBody.get("totalElements").asLong()).isPositive();
    }
}
