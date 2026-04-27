package com.devilsvault.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class AuditEventServiceTest {

    @Autowired
    AuditEventService service;

    @Autowired
    AuditEventRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @Transactional
    void clear() {
        repo.deleteAllInBatch();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        repo.deleteAllInBatch();
    }

    @Test
    void recordsEventsWithLinkedHashChain() {
        AuditEvent first = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of("ip", "127.0.0.1"));
        AuditEvent second = service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "10", Map.of("amount", "5.00"));
        AuditEvent third = service.record(AuditEventType.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE,
                "mallory", "user", null, Map.of("reason", "bad_password"));

        assertThat(first.getPrevHash()).isNull();
        assertThat(first.getEntryHash()).isNotBlank().hasSize(64);
        assertThat(second.getPrevHash()).isEqualTo(first.getEntryHash());
        assertThat(third.getPrevHash()).isEqualTo(second.getEntryHash());

        assertThat(service.verifyChain().valid()).isTrue();
    }

    @Test
    void verificationDetectsTamperedPayload() {
        service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of());
        AuditEvent toTamper = service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "10", Map.of("amount", "5.00"));
        service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "11", Map.of("amount", "1.00"));

        // Tamper at the SQL layer — JPA marks audit columns updatable=false on
        // purpose, so a hostile actor with DB access is the realistic threat
        // model. Verification must catch it.
        jdbc.update("UPDATE audit_event SET payload = ? WHERE id = ?",
                "{\"amount\":\"5000.00\"}", toTamper.getId());

        AuditEventService.ChainStatus status = service.verifyChain();
        assertThat(status.valid()).isFalse();
        assertThat(status.firstInvalidId()).isEqualTo(toTamper.getId());
        assertThat(status.reason()).contains("entry_hash");
    }

    @Test
    void verificationDetectsBrokenLink() {
        AuditEvent first = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of());
        AuditEvent second = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "bob", "user", "2", Map.of());

        jdbc.update("UPDATE audit_event SET prev_hash = ? WHERE id = ?",
                "0".repeat(64), second.getId());

        AuditEventService.ChainStatus status = service.verifyChain();
        assertThat(status.valid()).isFalse();
        assertThat(status.firstInvalidId()).isEqualTo(second.getId());
        assertThat(status.reason()).contains("prev_hash");

        // The original first event remains untouched.
        List<AuditEvent> all = repo.findAllByOrderByIdAsc();
        assertThat(all.get(0).getId()).isEqualTo(first.getId());
    }

    @Test
    void payloadIsCanonicalisedDeterministically() {
        AuditEvent a = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", new java.util.LinkedHashMap<>(Map.of("b", 2, "a", 1)));
        AuditEvent b = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", new java.util.LinkedHashMap<>(Map.of("a", 1, "b", 2)));
        // Different map insertion order, same canonical JSON => same payload column.
        assertThat(a.getPayload()).isEqualTo(b.getPayload());
    }
}
