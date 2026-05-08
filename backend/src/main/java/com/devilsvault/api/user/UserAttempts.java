package com.devilsvault.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Per-user failed-login counter, ported from the legacy {@code user_attempts} table consumed by
 * {@code controllers/security/LimitLoginAuthenticationProvider}.
 *
 * <p>Modelled as a {@code @MapsId} side table off {@link User} so the counter row shares the
 * user's primary key — the legacy schema relied on a non-unique {@code username} string, which we
 * replace with a foreign-key relationship that JPA can validate.
 */
@Entity
@Table(name = "user_attempts")
public class UserAttempts {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int attempts;

    @UpdateTimestamp
    @Column(name = "last_modified", nullable = false)
    private OffsetDateTime lastModified;

    public Long getUserId() { return userId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public OffsetDateTime getLastModified() { return lastModified; }
}
