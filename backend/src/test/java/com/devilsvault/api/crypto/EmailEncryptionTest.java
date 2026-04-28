package com.devilsvault.api.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.util.HashSet;
import java.util.Set;
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
class EmailEncryptionTest {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired EncryptionService encryptionService;
    @Autowired WebApplicationContext wac;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .addFilter(springSecurityFilterChain).build();
    }

    @Test
    void roundTrip_encryptThenDecrypt_returnsOriginal() {
        String original = "sensitive@example.com";
        byte[] encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String email = "same@example.com";
        byte[] first = encryptionService.encrypt(email);
        byte[] second = encryptionService.encrypt(email);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void sha256Hex_isDeterministic() {
        String email = "hash-test@example.com";
        String hash1 = encryptionService.sha256Hex(email);
        String hash2 = encryptionService.sha256Hex(email);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256Hex_noCollisionsForDistinctEmails() {
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String hash = encryptionService.sha256Hex("user" + i + "@example.com");
            assertThat(hashes.add(hash))
                    .as("Hash collision detected for user%d@example.com", i)
                    .isTrue();
        }
    }

    @Test
    void userEntity_emailRoundTrip_viaPersistence() {
        User u = new User();
        u.setUsername("enc_test_" + System.nanoTime());
        u.setEmail("encrypted@example.com");
        u.setEmailSearchHash(encryptionService.sha256Hex("encrypted@example.com"));
        u.setPasswordHash(encoder.encode("password"));
        u.setFullName("Encryption Test");
        u.setRole(Role.CUSTOMER);
        users.save(u);

        User loaded = users.findById(u.getId()).orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("encrypted@example.com");
    }

    @Test
    void existsByEmailSearchHash_findsUser() {
        String email = "hashfind@example.com";
        String hash = encryptionService.sha256Hex(email.toLowerCase(java.util.Locale.ROOT));

        User u = new User();
        u.setUsername("hash_find_" + System.nanoTime());
        u.setEmail(email);
        u.setEmailSearchHash(hash);
        u.setPasswordHash(encoder.encode("password"));
        u.setFullName("Hash Find");
        u.setRole(Role.CUSTOMER);
        users.save(u);

        assertThat(users.existsByEmailSearchHash(hash)).isTrue();
        assertThat(users.existsByEmailSearchHash("nonexistent_hash_value_xxxx")).isFalse();
    }

    @Test
    void register_duplicateEmail_isRejected() throws Exception {
        MockMvc mvc = mvc();

        long ts = System.nanoTime();
        String body1 = String.format(
                "{\"username\":\"dup1_%d\",\"email\":\"dup_%d@example.com\","
                        + "\"password\":\"password1\",\"fullName\":\"Dup1\"}",
                ts, ts);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isOk());

        String body2 = String.format(
                "{\"username\":\"dup2_%d\",\"email\":\"dup_%d@example.com\","
                        + "\"password\":\"password2\",\"fullName\":\"Dup2\"}",
                ts, ts);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isConflict());
    }
}
