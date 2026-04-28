package com.devilsvault.api.auth;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = jwtService.getRsaPublicKey();
        Map<String, Object> key = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", jwtService.getKid(),
                "n", base64UrlEncode(pub.getModulus().toByteArray()),
                "e", base64UrlEncode(pub.getPublicExponent().toByteArray()));
        return Map.of("keys", List.of(key));
    }

    private static String base64UrlEncode(byte[] input) {
        byte[] data = input;
        // Strip leading zero byte if present (BigInteger two's complement)
        if (data.length > 1 && data[0] == 0) {
            byte[] trimmed = new byte[data.length - 1];
            System.arraycopy(data, 1, trimmed, 0, trimmed.length);
            data = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
