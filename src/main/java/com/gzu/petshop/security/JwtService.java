package com.gzu.petshop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

/**
 * 签发与校验 JWT 访问令牌（HS256）。密码仅存 BCrypt 哈希，不写入 Token。
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String rawSecret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(toHmacSha256KeyBytes(rawSecret));
        this.expirationMs = expirationMs;
    }

    /**
     * HS256 要求密钥长度至少 256 bit（32 字节），不足则循环填充。
     */
    private static byte[] toHmacSha256KeyBytes(String secret) {
        byte[] src = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (src.length >= 32) {
            return Arrays.copyOf(src, src.length);
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = src.length == 0 ? (byte) i : src[i % src.length];
        }
        return out;
    }

    /**
     * @param role          角色：USER / MERCHANT / SUPER / OPERATOR（与登录主体一致）
     * @param principalId   主体主键：userId / merchantId / adminId
     */
    public String createAccessToken(String role, long principalId) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(principalId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String compactJwt) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(compactJwt)
                .getPayload();
    }
}
