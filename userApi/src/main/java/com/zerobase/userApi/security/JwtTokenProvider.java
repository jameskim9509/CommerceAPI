package com.zerobase.userApi.security;

import com.mysql.cj.util.StringUtils;
import com.zerobase.userApi.service.CustomerService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final long TOKEN_EXPIRE_TIME = 1000 * 60 * 60;

    private final CustomerService customerService;

    private String secretKey = "emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=";

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email)
    {
        Date now = new Date();

        Claims claims = Jwts.claims()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TOKEN_EXPIRE_TIME));

        String token = Jwts.builder()
                .setClaims(claims)
                .signWith(getSigningKey())
                .compact();

        return token;
    }

    public Authentication getAuthentication(String jwt)
    {
        String email = getEmail(jwt);
        UserDetails userDetails = customerService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(
                userDetails.getUsername(), "", userDetails.getAuthorities()
        );
    }

    public String getEmail(String token)
    {
        return this.parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        if (StringUtils.isEmptyOrWhitespaceOnly(token)) return false;

        var claims = this.parseClaims(token);
        return !claims.getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
