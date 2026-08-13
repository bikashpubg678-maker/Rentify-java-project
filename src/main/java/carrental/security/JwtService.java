package carrental.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates HS256 JWTs for the mobile REST API.
 * The token is stateless; the mobile client stores it in secure storage
 * and sends it as "Authorization: Bearer <token>" on every request.
 */
@Service
public class JwtService {

    @Value("${rentify.jwt.secret:CHANGE-ME-rentify-default-secret-please-override-32bytes!!}")
    private String secret;

    @Value("${rentify.jwt.ttl-millis:2592000000}") // 30 days
    private long ttlMillis;

    private SecretKey key;

    @PostConstruct
    void init() {
        // HS256 requires at least 256 bits (32 bytes) of key material.
        // Refuse to start with a short / placeholder secret in production.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "rentify.jwt.secret must be at least 32 bytes; got " + bytes.length
                + ". Set RENTIFY_JWT_SECRET to a strong random value.");
        }
        if (secret.contains("CHANGE-ME") || secret.contains("please-override")) {
            throw new IllegalStateException(
                "rentify.jwt.secret is still the placeholder default. "
                + "Set RENTIFY_JWT_SECRET to a strong random value before starting.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(Long userId, String email, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of("email", email, "role", role))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }
}