package com.devilsvault.api.auth;

import com.devilsvault.api.audit.CorrelationIdFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final JtiRevocationCache jtiRevocationCache;

    public JwtAuthenticationFilter(JwtService jwt, JtiRevocationCache jtiRevocationCache) {
        this.jwt = jwt;
        this.jtiRevocationCache = jtiRevocationCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwt.parse(token);
                Boolean mfaPending = claims.get("mfa_pending", Boolean.class);
                if (Boolean.TRUE.equals(mfaPending)) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(req, res);
                    return;
                }
                String jti = claims.getId();
                if (jti != null && jtiRevocationCache.isRevoked(jti)) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(req, res);
                    return;
                }
                String username = claims.getSubject();
                String scope = claims.get("scope", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + scope)));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
                CorrelationIdFilter.setActorFromPrincipal(auth);
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
