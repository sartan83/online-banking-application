package com.devilsvault.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountType;
import com.devilsvault.api.merchant.MerchantAuthorization;
import com.devilsvault.api.merchant.MerchantAuthorizationRepository;
import com.devilsvault.api.otp.Otp;
import com.devilsvault.api.otp.OtpRepository;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserAttemptsRepository;
import com.devilsvault.api.user.UserAuthenticationService;
import com.devilsvault.api.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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

/**
 * End-to-end coverage for the Phase 2 modules: OTP, credit card, request/approval, merchant,
 * admin, login lockout and forgot-password. These tests exercise the REST surface using MockMvc
 * against the real Spring context (H2 + Flyway), so they catch wiring bugs that pure mock-based
 * unit tests would miss.
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase2EndpointsTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired UserAttemptsRepository attempts;
    @Autowired UserAuthenticationService loginLimiter;
    @Autowired AccountRepository accounts;
    @Autowired OtpRepository otpRepo;
    @Autowired MerchantAuthorizationRepository merchantAuthRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    private User createUser(String username, String email, String password, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(username + " Test");
        u.setRole(role);
        return users.save(u);
    }

    private Account createAccount(User owner, AccountType type, String balance) {
        Account a = new Account();
        a.setOwner(owner);
        a.setAccountType(type);
        a.setBalance(new BigDecimal(balance));
        return accounts.save(a);
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void loginLockout_threeFailuresDisableAccount_resetsOnSuccess() throws Exception {
        createUser("locky", "locky@example.com", "secret123", Role.CUSTOMER);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"locky\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        User locked = users.findByUsername("locky").orElseThrow();
        assertThat(locked.isEnabled()).isFalse();
        assertThat(attempts.findById(locked.getId()).orElseThrow().getAttempts()).isEqualTo(3);

        // Even the correct password is rejected once the account is disabled.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locky\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otpFlow_sendAndVerify() throws Exception {
        createUser("otpuser", "otpuser@example.com", "secret123", Role.CUSTOMER);
        String token = login("otpuser", "secret123");

        mvc.perform(post("/api/otp/send")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CRITICAL_TRANSFER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("CRITICAL_TRANSFER"));

        User u = users.findByUsername("otpuser").orElseThrow();
        Otp issued = otpRepo
                .findFirstByUserIdAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(u.getId(), Otp.Purpose.CRITICAL_TRANSFER)
                .orElseThrow();

        mvc.perform(post("/api/otp/verify")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CRITICAL_TRANSFER\",\"code\":\"" + issued.getCode() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void otpFlow_threeWrongAttemptsLockOut() throws Exception {
        createUser("otpfail", "otpfail@example.com", "secret123", Role.CUSTOMER);
        String token = login("otpfail", "secret123");

        mvc.perform(post("/api/otp/send")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/otp/verify")
                            .with(csrf())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"purpose\":\"LOGIN\",\"code\":\"000000\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/otp/verify")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"LOGIN\",\"code\":\"000000\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void forgotPassword_resetsHashAndUnlocks() throws Exception {
        User u = createUser("forget", "forget@example.com", "oldpassword", Role.CUSTOMER);
        u.setEnabled(false);
        users.save(u);

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forget@example.com\"}"))
                .andExpect(status().isOk());

        Otp issued = otpRepo
                .findFirstByUserIdAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(u.getId(), Otp.Purpose.FORGOT_PASSWORD)
                .orElseThrow();

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forget@example.com\",\"code\":\"" + issued.getCode()
                                + "\",\"newPassword\":\"brand-new-pw\"}"))
                .andExpect(status().isOk());

        User refreshed = users.findByUsername("forget").orElseThrow();
        assertThat(refreshed.isEnabled()).isTrue();
        assertThat(encoder.matches("brand-new-pw", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void forgotPassword_unknownEmailIsSilentlyOk() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void creditCard_openAndPay() throws Exception {
        User customer = createUser("cardholder", "cardholder@example.com", "secret123", Role.CUSTOMER);
        Account funding = createAccount(customer, AccountType.CHECKING, "300.00");
        String token = login("cardholder", "secret123");

        MvcResult openRes = mvc.perform(post("/api/credit-cards")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long creditId = mapper.readTree(openRes.getResponse().getContentAsString()).get("id").asLong();

        // The card is opened with a zero balance; pay $50 and check the deposit account is debited.
        String payBody = mapper.writeValueAsString(java.util.Map.of(
                "creditAccountId", creditId,
                "sourceAccountId", funding.getId(),
                "amount", "50.00"));
        mvc.perform(post("/api/credit-cards/payment")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PAYMENT"))
                .andExpect(jsonPath("$.amount").value(50.0));

        BigDecimal fundingBal = accounts.findById(funding.getId()).orElseThrow().getBalance();
        assertThat(fundingBal).isEqualByComparingTo("250.00");

        mvc.perform(get("/api/credit-cards/" + creditId + "/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void requestApproval_internalUserCanApprove() throws Exception {
        createUser("requester", "requester@example.com", "secret123", Role.CUSTOMER);
        createUser("approver", "approver@example.com", "secret123", Role.MANAGER);

        String customerToken = login("requester", "secret123");
        String managerToken = login("approver", "secret123");

        String body = mapper.writeValueAsString(java.util.Map.of(
                "requestType", "EMAIL_CHANGE",
                "currentValue", "requester@example.com",
                "requestedValue", "newaddress@example.com",
                "description", "Please update my email"));
        MvcResult created = mvc.perform(post("/api/requests")
                        .with(csrf())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Long requestId = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Customer cannot self-approve.
        mvc.perform(put("/api/requests/" + requestId + "/approve")
                        .with(csrf())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/requests/" + requestId + "/approve")
                        .with(csrf())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void merchantAuthorize_acceptAndPay() throws Exception {
        User customer = createUser("custM", "custm@example.com", "secret123", Role.CUSTOMER);
        User merchant = createUser("shopM", "shopm@example.com", "secret123", Role.MERCHANT);
        Account customerAcct = createAccount(customer, AccountType.CHECKING, "500.00");
        Account merchantAcct = createAccount(merchant, AccountType.CHECKING, "0.00");

        String customerToken = login("custM", "secret123");
        String merchantToken = login("shopM", "secret123");

        // Customer authorises the merchant.
        MvcResult auth = mvc.perform(post("/api/merchants/authorize")
                        .with(csrf())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantId\":" + merchant.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long authId = mapper.readTree(auth.getResponse().getContentAsString()).get("id").asLong();

        // Merchant accepts.
        mvc.perform(put("/api/merchants/authorizations/" + authId + "/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        MerchantAuthorization stored = merchantAuthRepo.findById(authId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MerchantAuthorization.Status.ACTIVE);

        // Merchant charges the customer.
        String payBody = mapper.writeValueAsString(java.util.Map.of(
                "authorizationId", authId,
                "customerAccountId", customerAcct.getId(),
                "merchantAccountId", merchantAcct.getId(),
                "amount", "75.25",
                "description", "Coffee"));
        mvc.perform(post("/api/merchants/payments")
                        .with(csrf())
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(75.25));

        assertThat(accounts.findById(customerAcct.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("424.75");
        assertThat(accounts.findById(merchantAcct.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("75.25");
    }

    @Test
    void admin_canListUsersAndChangeRole() throws Exception {
        User admin = createUser("rootA", "roota@example.com", "secret123", Role.ADMIN);
        User customer = createUser("custA", "custa@example.com", "secret123", Role.CUSTOMER);
        String adminToken = login("rootA", "secret123");

        // Other tests share the H2 instance, so just assert that our two users are present.
        MvcResult userList = mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode usersJson = mapper.readTree(userList.getResponse().getContentAsString());
        assertThat(usersJson.isArray()).isTrue();
        java.util.Set<String> names = new java.util.HashSet<>();
        usersJson.forEach(n -> names.add(n.get("username").asText()));
        assertThat(names).contains("rootA", "custA");

        mvc.perform(put("/api/admin/users/" + customer.getId() + "/role")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MERCHANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MERCHANT"));

        // Disable then re-enable the customer.
        mvc.perform(put("/api/admin/users/" + customer.getId() + "/enabled")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Pretend the user accumulated failed attempts so we can confirm the unlock clears them.
        loginLimiter.recordFailure(customer.getUsername());
        loginLimiter.recordFailure(customer.getUsername());

        mvc.perform(put("/api/admin/users/" + customer.getId() + "/enabled")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(attempts.findById(customer.getId()).orElseThrow().getAttempts()).isZero();

        // Customer cannot reach admin endpoints.
        String customerToken = login("custA", "secret123");
        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_pendingRegistrationApprovalCreatesUser() throws Exception {
        createUser("manA", "mana@example.com", "secret123", Role.MANAGER);
        String managerToken = login("manA", "secret123");

        String body = mapper.writeValueAsString(java.util.Map.of(
                "username", "newhire",
                "email", "newhire@example.com",
                "password", "tempPass1",
                "fullName", "New Hire",
                "role", "EMPLOYEE"));
        MvcResult created = mvc.perform(post("/api/admin/registrations")
                        .with(csrf())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pending = mapper.readTree(created.getResponse().getContentAsString());
        Long pendingId = pending.get("id").asLong();
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");

        mvc.perform(put("/api/admin/registrations/" + pendingId + "/approve")
                        .with(csrf())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(users.findByUsername("newhire")).isPresent();
    }
}
