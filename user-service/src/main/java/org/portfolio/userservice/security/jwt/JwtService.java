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

    public JwtAuthenticationDto generateAuthToken(String email) {
        JwtAuthenticationDto jwtDto=new JwtAuthenticationDto();
        jwtDto.setToken(generateJwtToken(email));
        jwtDto.setRefreshToken(generateRefreshToken(email));
        return jwtDto;
    }

    public String getEmailFromToken(String token){
        Claims claims= Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return  claims.getSubject();
    }

    public boolean validateJwtToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        }catch (ExpiredJwtException exception){
            log.error("Expired JwtException",exception);
        }catch (UnsupportedJwtException exception){
            log.error("Unsupported JwtException",exception);
        }catch (MalformedJwtException exception){
            log.error("Malformed JwtException",exception);
        }catch (SecurityException exception){
            log.error("Security JwtException",exception);
        }catch (Exception exception){
            log.error("invalid token", exception);
        }
        return false;
    }

    public JwtAuthenticationDto refreshBaseToken(String email, String refreshToken){
        JwtAuthenticationDto jwtDto=new JwtAuthenticationDto();
        jwtDto.setToken(generateJwtToken(email));
        jwtDto.setRefreshToken(refreshToken);
        return jwtDto;
    }


    private String generateJwtToken(String email){
        Date date=Date.from(LocalDateTime.now().plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .expiration(date)
                .signWith(secretKey)
                .compact();
    }
    private String generateRefreshToken(String email){
        Date date=Date.from(LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .subject(email)
                .expiration(date)
                .signWith(secretKey)
                .compact();
    }

}