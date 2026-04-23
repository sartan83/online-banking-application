package com.devilsvault.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.beans.factory.annotation.Qualifier;

@SpringBootTest
@ActiveProfiles("test")
class AuthAndTransferTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    @Test
    void register_login_listAccounts_transfer() throws Exception {
        MockMvc mvc = mvc();

        // register
        String registerBody = """
            {"username":"alice","email":"alice@example.com","password":"password1","fullName":"Alice"}
            """;
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andReturn();
        String token = mapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        assertThat(token).isNotBlank();

        // seed two accounts for alice
        User alice = users.findByUsername("alice").orElseThrow();
        Account src = new Account();
        src.setOwner(alice);
        src.setAccountType(AccountType.CHECKING);
        src.setBalance(new BigDecimal("500.00"));
        accounts.save(src);
        Account tgt = new Account();
        tgt.setOwner(alice);
        tgt.setAccountType(AccountType.SAVINGS);
        tgt.setBalance(new BigDecimal("0.00"));
        accounts.save(tgt);

        // list accounts
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // transfer
        String transferBody = mapper.writeValueAsString(java.util.Map.of(
                "sourceAccountId", src.getId(),
                "targetAccountId", tgt.getId(),
                "amount", "125.50",
                "description", "rent"));
        MvcResult tr = mvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(tr.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");

        BigDecimal srcBal = accounts.findById(src.getId()).orElseThrow().getBalance();
        BigDecimal tgtBal = accounts.findById(tgt.getId()).orElseThrow().getBalance();
        assertThat(srcBal).isEqualByComparingTo("374.50");
        assertThat(tgtBal).isEqualByComparingTo("125.50");
    }

    @Test
    void login_rejectsBadPassword() throws Exception {
        User u = new User();
        u.setUsername("bob");
        u.setEmail("bob@example.com");
        u.setPasswordHash(encoder.encode("correct-horse"));
        u.setFullName("Bob");
        u.setRole(Role.CUSTOMER);
        users.save(u);

        mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
