package com.zerobase.userApi.security;

import com.mysql.cj.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException
    {
        String token = resolveTokenFromRequest(request);
        if(token != null &&
                !token.equalsIgnoreCase("null")
                            && jwtTokenProvider.validateToken(token))
        {
            String email = jwtTokenProvider.getEmail(token);
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.info(String.format("[%s] -> %s", email, request.getRequestURI()));
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTokenFromRequest(HttpServletRequest request)
    {
        String token = request.getHeader(TOKEN_HEADER);

        if(!StringUtils.isEmptyOrWhitespaceOnly(token) && token.startsWith(TOKEN_PREFIX))
        {
            return token.substring(TOKEN_PREFIX.length());
        }
        else return null;
    }
}
