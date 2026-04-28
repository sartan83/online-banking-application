package com.devilsvault.api.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountStatus;
import com.devilsvault.api.account.AccountType;
import com.devilsvault.api.auth.JwtService;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AdminIntegrationTest {

    @Autowired WebApplicationContext wac;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;
    private String adminToken;
    private String userToken;
    private User testUser;
    private Account checking;
    private Account savings;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();

        User admin = new User();
        admin.setUsername("testadmin_" + System.nanoTime());
        admin.setEmail("testadmin_" + System.nanoTime() + "@test.com");
        admin.setPasswordHash(encoder.encode("adminpass"));
        admin.setFullName("Test Admin");
        admin.setRole(Role.ADMIN);
        admin.setMfaEnabled(true);
        users.save(admin);
        adminToken = jwtService.issue(admin.getUsername(), admin.getRole().name());

        testUser = new User();
        testUser.setUsername("testuser_" + System.nanoTime());
        testUser.setEmail("testuser_" + System.nanoTime() + "@test.com");
        testUser.setPasswordHash(encoder.encode("userpass"));
        testUser.setFullName("Test User");
        testUser.setRole(Role.CUSTOMER);
        users.save(testUser);
        userToken = jwtService.issue(testUser.getUsername(), testUser.getRole().name());

        checking = new Account();
        checking.setOwner(testUser);
        checking.setAccountType(AccountType.CHECKING);
        checking.setBalance(new BigDecimal("1000.00"));
        accounts.save(checking);

        savings = new Account();
        savings.setOwner(testUser);
        savings.setAccountType(AccountType.SAVINGS);
        savings.setBalance(new BigDecimal("500.00"));
        accounts.save(savings);
    }

    @Test
    void adminEndpoints_denyCustomerRole() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/freeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_returnsPage() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listUsers_searchByUsername() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .param("q", testUser.getUsername())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(testUser.getUsername()));
    }

    @Test
    void getUser_returnsDetailWithAccounts() throws Exception {
        mvc.perform(get("/api/admin/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUser.getUsername()))
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts.length()").value(2));
    }

    @Test
    void getUser_notFound() throws Exception {
        mvc.perform(get("/api/admin/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void freezeAndUnfreeze_account() throws Exception {
        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/freeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/unfreeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void freeze_blocksTransfer_asSource() throws Exception {
        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/freeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String transferBody = mapper.writeValueAsString(Map.of(
                "sourceAccountId", checking.getId(),
                "targetAccountId", savings.getId(),
                "amount", "10.00"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freeze_blocksTransfer_asTarget() throws Exception {
        mvc.perform(post("/api/admin/accounts/" + savings.getId() + "/freeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String transferBody = mapper.writeValueAsString(Map.of(
                "sourceAccountId", checking.getId(),
                "targetAccountId", savings.getId(),
                "amount", "10.00"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unfreeze_reAllowsTransfer() throws Exception {
        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/freeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/accounts/" + checking.getId() + "/unfreeze")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String transferBody = mapper.writeValueAsString(Map.of(
                "sourceAccountId", checking.getId(),
                "targetAccountId", savings.getId(),
                "amount", "10.00"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk());
    }

    @Test
    void auditIntegrity_validChain() throws Exception {
        mvc.perform(get("/api/admin/audit/integrity")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.brokenAtId").isEmpty())
                .andExpect(jsonPath("$.totalEntries").isNumber());
    }

    @Test
    void auditLog_returnsPaginatedResults() throws Exception {
        mvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void meEndpoint_returnsCurrentUser() throws Exception {
        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void meEndpoint_requiresAuth() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isForbidden());
    }
}
