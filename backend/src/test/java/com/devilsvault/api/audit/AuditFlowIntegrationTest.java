package com.devilsvault.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountType;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AuditFlowIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    CorrelationIdFilter correlationIdFilter;

    @Autowired
    UserRepository users;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AuditEventRepository auditRepo;

    @Autowired
    AuditEventService auditService;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    @Transactional
    void clearAudit() {
        auditRepo.deleteAllInBatch();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .addFilter(correlationIdFilter)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    void registerLoginAndTransferEmitAuditEvents() throws Exception {
        MockMvc mvc = mvc();

        String registerBody = """
            {"username":"audit-alice","email":"audit-alice@example.com","password":"password1","fullName":"Alice"}
            """;
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody)
                        .header(CorrelationIdFilter.HEADER, "test-corr-1"))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // Wrong password → AUTH_LOGIN_FAILURE.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audit-alice\",\"password\":\"nope\"}")
                        .header(CorrelationIdFilter.HEADER, "test-corr-2"))
                .andExpect(status().isUnauthorized());

        // Correct password → AUTH_LOGIN_SUCCESS.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audit-alice\",\"password\":\"password1\"}")
                        .header(CorrelationIdFilter.HEADER, "test-corr-3"))
                .andExpect(status().isOk());

        // Seed accounts and run a successful transfer → TRANSFER_COMPLETED.
        User alice = users.findByUsername("audit-alice").orElseThrow();
        Account src = new Account();
        src.setOwner(alice);
        src.setAccountType(AccountType.CHECKING);
        src.setBalance(new BigDecimal("100.00"));
        accounts.save(src);
        Account tgt = new Account();
        tgt.setOwner(alice);
        tgt.setAccountType(AccountType.SAVINGS);
        tgt.setBalance(new BigDecimal("0.00"));
        accounts.save(tgt);

        String transferBody = mapper.writeValueAsString(java.util.Map.of(
                "sourceAccountId", src.getId(),
                "targetAccountId", tgt.getId(),
                "amount", "25.00",
                "description", "rent"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationIdFilter.HEADER, "test-corr-4")
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk());

        // Failing transfer → TRANSFER_REJECTED.
        String overdraftBody = mapper.writeValueAsString(java.util.Map.of(
                "sourceAccountId", src.getId(),
                "targetAccountId", tgt.getId(),
                "amount", "9999.00",
                "description", "overdraft"));
        mvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationIdFilter.HEADER, "test-corr-5")
                        .contentType(MediaType.APPLICATION_JSON).content(overdraftBody))
                .andExpect(status().isBadRequest());

        List<AuditEvent> events = auditRepo.findByActorUsernameOrderByIdAsc("audit-alice");
        assertThat(events).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.AUTH_REGISTER_SUCCESS,
                AuditEventType.AUTH_LOGIN_FAILURE,
                AuditEventType.AUTH_LOGIN_SUCCESS,
                AuditEventType.TRANSFER_COMPLETED,
                AuditEventType.TRANSFER_REJECTED);

        // Correlation IDs propagate end-to-end.
        assertThat(events).extracting(AuditEvent::getCorrelationId)
                .containsExactly("test-corr-1", "test-corr-2", "test-corr-3", "test-corr-4", "test-corr-5");

        // Outcomes match the event semantics.
        assertThat(events).extracting(AuditEvent::getOutcome).containsExactly(
                AuditOutcome.SUCCESS, AuditOutcome.FAILURE, AuditOutcome.SUCCESS,
                AuditOutcome.SUCCESS, AuditOutcome.FAILURE);

        // Hash chain is intact across the whole sequence.
        assertThat(auditService.verifyChain().valid()).isTrue();
    }
}
