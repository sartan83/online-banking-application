package com.devilsvault.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "devilsvault";
    private static final String AUDIENCE = "devilsvault-api";
    private static final int RSA_KEY_SIZE = 2048;

    @Value("${app.security.jwt.expiration-minutes}")
    private long expirationMinutes;

    @Value("${app.security.jwt.private-key-pem:#{null}}")
    private String privateKeyPemPath;

    @Value("${app.security.jwt.public-key-pem:#{null}}")
    private String publicKeyPemPath;

    @Value("${app.security.jwt.kid:#{null}}")
    private String configuredKid;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String kid;

    @PostConstruct
    void init() {
        if (privateKeyPemPath != null && publicKeyPemPath != null) {
            try {
                this.privateKey = loadPrivateKey(Path.of(privateKeyPemPath));
                this.publicKey = loadPublicKey(Path.of(publicKeyPemPath));
                this.kid = configuredKid != null ? configuredKid : "prod-1";
                LOG.info("RS256 key pair loaded from PEM files, kid={}", kid);
            } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new IllegalStateException("Failed to load RSA key pair from PEM files", ex);
            }
        } else {
            LOG.warn("No JWT PEM key files configured — generating ephemeral RS256 key pair. "
                    + "Set app.security.jwt.private-key-pem and app.security.jwt.public-key-pem for production.");
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(RSA_KEY_SIZE);
                KeyPair pair = gen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                this.kid = "ephemeral-" + UUID.randomUUID().toString().substring(0, 8);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("RSA not available", ex);
            }
        }
    }

    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(username)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .id(UUID.randomUUID().toString())
                .claim("scope", role)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getKid() {
        return kid;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    private static PrivateKey loadPrivateKey(Path path)
            throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        String pem = Files.readString(path);
        String base64 = stripPem(pem);
        byte[] decoded = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PublicKey loadPublicKey(Path path)
            throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        String pem = Files.readString(path);
        String base64 = stripPem(pem);
        byte[] decoded = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String stripPem(String pem) {
        StringBuilder sb = new StringBuilder(pem.length());
        try (Reader reader = new StringReader(pem);
             java.io.BufferedReader br = new java.io.BufferedReader(reader)) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!line.startsWith("-----")) {
                    sb.append(line.trim());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to strip PEM headers", ex);
        }
        return sb.toString();
    }

    public RSAPublicKey getRsaPublicKey() {
        return (RSAPublicKey) publicKey;
    }
}
