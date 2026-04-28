package db.migration;

import com.devilsvault.api.crypto.EncryptionUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Flyway Java migration that encrypts existing plaintext email values
 * and populates the deterministic search-hash column.
 *
 * <ul>
 *   <li>On <b>PostgreSQL</b>: enables the pgcrypto extension (required by
 *       DORA Annex I for database-level crypto primitives).</li>
 *   <li>On <b>all databases</b>: reads every {@code app_user} row that still
 *       has a plaintext email, encrypts it with AES-256-GCM via
 *       {@link EncryptionUtil}, and stores the SHA-256 lookup hash.</li>
 * </ul>
 *
 * <p>The encryption key is read from {@code DB_ENCRYPTION_KEY} (env var or
 * system property).  For H2-based tests a fixed dev key is used when the
 * variable is absent.</p>
 */
public class V7_1__Migrate_email_encryption extends BaseJavaMigration {

    private static final String TEST_KEY =
            "zuzU9ht1V8QggzT7DbEsnPGfoMYW8sksbtPJRvw7YGQ=";

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        boolean isPostgres = "PostgreSQL".equalsIgnoreCase(
                conn.getMetaData().getDatabaseProductName());

        if (isPostgres) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            }
        }

        String base64Key = EncryptionUtil.resolveKey();
        if (base64Key.isEmpty()) {
            if (isPostgres) {
                throw new IllegalStateException(
                        "DB_ENCRYPTION_KEY env var or system property must be set for production migration");
            }
            base64Key = TEST_KEY;
        }
        byte[] key = EncryptionUtil.decodeKey(base64Key);

        try (Statement query = conn.createStatement();
             ResultSet rs = query.executeQuery(
                     "SELECT id, email FROM app_user "
                             + "WHERE email IS NOT NULL AND email_enc IS NULL")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String email = rs.getString("email");
                byte[] encrypted = EncryptionUtil.encrypt(email, key);
                String hash = EncryptionUtil.sha256Hex(email.toLowerCase(Locale.ROOT));
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE app_user SET email_enc = ?, "
                                + "email_search_hash = ? WHERE id = ?")) {
                    update.setBytes(1, encrypted);
                    update.setString(2, hash);
                    update.setLong(3, id);
                    update.executeUpdate();
                }
            }
        }
    }
}
