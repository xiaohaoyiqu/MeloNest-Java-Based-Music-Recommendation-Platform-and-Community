package com.haoran.music.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

   
                      
                      
   
@Component
public class JwtUtils {

       
                          
       
    @Value("${jwt.secret}")
    private String secret;

       
                    
       
    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("jwt.secret must contain at least 32 characters");
        }
        if (isProductionProfile(activeProfiles)
                && "change-me-jwt-secret-at-least-32-bytes-for-local-demo".equals(secret.trim())) {
            throw new IllegalStateException("JWT_SECRET must be configured for production profiles");
        }
    }

    private boolean isProductionProfile(String profiles) {
        if (profiles == null) {
            return false;
        }
        String normalized = profiles.toLowerCase();
        return normalized.contains("prod")
                || normalized.contains("linux")
                || normalized.contains("external");
    }

       
           
      
                        
       
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

       
              
      
                         
                    
       
    public String generateToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return generateToken(claims);
    }

       
                     
      
                          
                    
       
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(SignatureAlgorithm.HS256, getSignKey())
                .compact();
    }

       
                      
      
                         
                     
       
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

       
                    
      
                         
                   
       
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }

       
                  
      
                         
                               
       
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

       
              
      
                          
                     
       
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        claims.setId(UUID.randomUUID().toString());
        claims.setIssuedAt(new Date());
        claims.setExpiration(new Date(System.currentTimeMillis() + expiration));
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, getSignKey())
                .compact();
    }
}
