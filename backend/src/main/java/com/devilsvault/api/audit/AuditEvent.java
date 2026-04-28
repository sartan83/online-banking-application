package com.devilsvault.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Immutable row in the hash-chained audit log. The persistence layer never
 * updates rows — once written, they are read-only.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "actor_username", length = 64, updatable = false)
    private String actorUsername;

    @Column(name = "actor_ip", length = 64, updatable = false)
    private String actorIp;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "resource_type", length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 128, updatable = false)
    private String resourceId;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "prev_hash", length = 64, updatable = false)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, length = 64, updatable = false, unique = true)
    private String entryHash;

    public Long getId() { return id; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public AuditEventType getEventType() { return eventType; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getActorUsername() { return actorUsername; }
    public String getActorIp() { return actorIp; }
    public String getCorrelationId() { return correlationId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
    public String getPrevHash() { return prevHash; }
    public String getEntryHash() { return entryHash; }

    void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    void setEventType(AuditEventType v) { this.eventType = v; }
    void setOutcome(AuditOutcome v) { this.outcome = v; }
    void setActorUsername(String v) { this.actorUsername = v; }
    void setActorIp(String v) { this.actorIp = v; }
    void setCorrelationId(String v) { this.correlationId = v; }
    void setResourceType(String v) { this.resourceType = v; }
    void setResourceId(String v) { this.resourceId = v; }
    void setPayload(String v) { this.payload = v; }
    void setPrevHash(String v) { this.prevHash = v; }
    void setEntryHash(String v) { this.entryHash = v; }
}
