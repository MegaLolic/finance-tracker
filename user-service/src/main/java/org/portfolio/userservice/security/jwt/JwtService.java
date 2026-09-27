package org.portfolio.userservice.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.portfolio.userservice.dto.JwtAuthenticationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret}") String secret) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

    }

    public JwtAuthenticationDto generateAuthToken(Long userId,String email) {
        JwtAuthenticationDto jwtDto=new JwtAuthenticationDto();
        jwtDto.setToken(generateJwtToken(userId,email));
        jwtDto.setRefreshToken(generateRefreshToken(userId,email));
        return jwtDto;
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("type", String.class));
        } catch (JwtException e) {
            log.error("Invalid token", e);
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (JwtException e) {
            log.error("Invalid refresh token", e);
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public JwtAuthenticationDto refreshBaseToken(String refreshToken){
        JwtAuthenticationDto jwtDto=new JwtAuthenticationDto();
        Long userId = getUserIdFromToken(refreshToken);
        String email = getEmailFromToken(refreshToken);
        jwtDto.setToken(generateJwtToken(userId,email));
        jwtDto.setRefreshToken(refreshToken);
        return jwtDto;
    }

    private Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }


    private String generateJwtToken(Long userId,String email){
        Date date=Date.from(LocalDateTime.now().plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .claim("userId",userId)
                .claim("email",email)
                .claim("type","access")
                .expiration(date)
                .signWith(secretKey)
                .compact();
    }
    private String generateRefreshToken(Long userId,String email){
        Date date=Date.from(LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .claim("userId",userId)
                .claim("email",email)
                .claim("type","refresh")
                .expiration(date)
                .signWith(secretKey)
                .compact();
    }

}