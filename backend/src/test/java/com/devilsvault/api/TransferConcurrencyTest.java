package com.devilsvault.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountType;
import com.devilsvault.api.transfer.TransferRequest;
import com.devilsvault.api.transfer.TransferService;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Asserts that concurrent transfers from the same source account preserve the conservation
 * invariant: total balance across source + target is unchanged regardless of how many
 * transfers succeed or fail. Without row locking this fails reliably because two transactions
 * can both pass the balance check, each computing a new balance from the same stale read, and
 * the second commit silently overwrites the first's deduction — creating money.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired TransferService transferService;
    @Autowired PasswordEncoder encoder;

    @Test
    void concurrentTransfers_preserveTotalBalance() throws Exception {
        User owner = new User();
        owner.setUsername("concurrent-user");
        owner.setEmail("cc@example.com");
        owner.setPasswordHash(encoder.encode("password1"));
        owner.setFullName("Concurrent");
        owner.setRole(Role.CUSTOMER);
        users.save(owner);

        BigDecimal initialSource = new BigDecimal("1000.00");
        Account src = new Account();
        src.setOwner(owner);
        src.setAccountType(AccountType.CHECKING);
        src.setBalance(initialSource);
        accounts.save(src);

        Account tgt = new Account();
        tgt.setOwner(owner);
        tgt.setAccountType(AccountType.SAVINGS);
        tgt.setBalance(BigDecimal.ZERO);
        accounts.save(tgt);

        int parallelism = 8;
        int transfersPerThread = 25;
        BigDecimal amount = new BigDecimal("10.00");

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < parallelism; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < transfersPerThread; j++) {
                    try {
                        transferService.execute("concurrent-user",
                                new TransferRequest(src.getId(), tgt.getId(), amount, "race"));
                        successes.incrementAndGet();
                    } catch (RuntimeException ex) {
                        failures.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        start.countDown();
        pool.shutdown();
        boolean terminated = pool.awaitTermination(60, TimeUnit.SECONDS);
        assertThat(terminated).as("thread pool did not terminate in time").isTrue();
        for (Future<?> f : futures) {
            f.get();
        }

        Account finalSrc = accounts.findById(src.getId()).orElseThrow();
        Account finalTgt = accounts.findById(tgt.getId()).orElseThrow();
        BigDecimal total = finalSrc.getBalance().add(finalTgt.getBalance());

        assertThat(total)
                .as("conservation: src + tgt must always equal the initial total (%s succeeded, %s failed)",
                        successes.get(), failures.get())
                .isEqualByComparingTo(initialSource);

        BigDecimal expectedTarget = amount.multiply(BigDecimal.valueOf(successes.get()));
        assertThat(finalTgt.getBalance())
                .as("target balance must match successful transfers")
                .isEqualByComparingTo(expectedTarget);
    }
}
