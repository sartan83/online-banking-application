package com.devilsvault.api.merchant;

import com.devilsvault.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Authorisation grant from a customer to a merchant, replacing the legacy
 * {@code authorize_merchant} table.
 *
 * <p>The legacy stack tracked merchant relationships through ad-hoc rows on the customer table;
 * the modern model is a first-class join entity with an {@link Status} lifecycle so revocation can
 * be expressed without deleting history.
 */
@Entity
@Table(name = "merchant_authorization", uniqueConstraints = {
        @UniqueConstraint(name = "uq_merchant_authorization_pair", columnNames = {"customer_id", "merchant_id"})
})
public class MerchantAuthorization {

    public enum Status { PENDING, ACTIVE, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }
    public User getMerchant() { return merchant; }
    public void setMerchant(User merchant) { this.merchant = merchant; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
