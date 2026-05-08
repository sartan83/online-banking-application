package com.devilsvault.api.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login attempt limiter ported from {@code controllers/security/LimitLoginAuthenticationProvider}
 * and {@code dao/user/UserDetailsDaoImpl}.
 *
 * <p>Three consecutive failed authentications disable the account ({@link User#setEnabled(boolean)
 * enabled = false}); a successful authentication resets the counter. Account re-enablement is
 * handled by the admin module.
 */
@Service
public class UserAuthenticationService {

    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final UserRepository users;
    private final UserAttemptsRepository attempts;

    public UserAuthenticationService(UserRepository users, UserAttemptsRepository attempts) {
        this.users = users;
        this.attempts = attempts;
    }

    @Transactional
    public void recordFailure(String username) {
        users.findByUsername(username).ifPresent(user -> {
            UserAttempts row = attempts.findById(user.getId()).orElseGet(() -> {
                UserAttempts fresh = new UserAttempts();
                fresh.setUser(user);
                return fresh;
            });
            row.setAttempts(row.getAttempts() + 1);
            attempts.save(row);
            if (row.getAttempts() >= MAX_FAILED_ATTEMPTS && user.isEnabled()) {
                user.setEnabled(false);
                users.save(user);
            }
        });
    }

    @Transactional
    public void recordSuccess(String username) {
        users.findByUsername(username).ifPresent(user -> attempts.findById(user.getId()).ifPresent(row -> {
            row.setAttempts(0);
            attempts.save(row);
        }));
    }

    @Transactional
    public void unlock(String username) {
        users.findByUsername(username).ifPresent(user -> {
            user.setEnabled(true);
            users.save(user);
            attempts.findById(user.getId()).ifPresent(row -> {
                row.setAttempts(0);
                attempts.save(row);
            });
        });
    }
}
