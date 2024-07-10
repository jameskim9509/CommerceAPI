package com.zerobase.orderApi.security;

import com.mysql.cj.util.StringUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String KEY_ROLES = "roles";

    private String secretKey = "emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=";

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Authentication getAuthentication(String jwt)
    {
        String email = getEmail(jwt);
        Long id = getId(jwt);

        List<String> roles = getRoles(jwt);

        CustomUserDetails userDetails = new CustomUserDetails(email, id, roles);
        return new UsernamePasswordAuthenticationToken(
                userDetails, "", userDetails.getAuthorities()
        );
    }

    public String getEmail(String token)
    {
        return this.parseClaims(token).getSubject();
    }

    private List<String> getRoles(String token)
    {
        return (List<String>) this.parseClaims(token).get(KEY_ROLES);
    }

    public Long getId(String token)
    {
        return Long.valueOf(this.parseClaims(token).getId());
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
