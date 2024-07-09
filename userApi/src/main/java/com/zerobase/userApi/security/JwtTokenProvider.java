package com.zerobase.userApi.security;

import com.mysql.cj.util.StringUtils;
import com.zerobase.userApi.service.customer.CustomerService;
import com.zerobase.userApi.service.seller.SellerService;
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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final long TOKEN_EXPIRE_TIME = 1000 * 60 * 60;
    private static final String KEY_ROLES = "roles";

    private final CustomerService customerService;
    private final SellerService sellerService;

    private String secretKey = "emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=emVyb2Jhc2U6Y29tbWVyY2VhcGk=";

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, List<String> roles, Long id)
    {
        Date now = new Date();

        Claims claims = Jwts.claims()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TOKEN_EXPIRE_TIME))
                .setId(id.toString());

        claims.put(KEY_ROLES, roles);

        String token = Jwts.builder()
                .setClaims(claims)
                .signWith(getSigningKey())
                .compact();

        return token;
    }

    public Authentication getAuthentication(String jwt)
    {
        String email = getEmail(jwt);
        List<String> roles = getRoles(jwt);

        if(roles.contains(Authority.CUSTOMER.getRole())) {
            UserDetails userDetails = customerService.loadUserByUsername(email);
            return new UsernamePasswordAuthenticationToken(
                    userDetails, "", userDetails.getAuthorities()
            );
        }
        else
        {
            UserDetails userDetails = sellerService.loadUserByUsername(email);
            return new UsernamePasswordAuthenticationToken(
                    userDetails, "", userDetails.getAuthorities()
            );
        }
    }

    public String getEmail(String token)
    {
        return this.parseClaims(token).getSubject();
    }

    private List<String> getRoles(String token)
    {
        return (List<String>) this.parseClaims(token).get(KEY_ROLES);
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
