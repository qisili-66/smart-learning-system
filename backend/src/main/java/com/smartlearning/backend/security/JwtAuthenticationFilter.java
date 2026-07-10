package com.smartlearning.backend.security;

import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(Constants.JWT_HEADER);
        if (authHeader != null && authHeader.startsWith(Constants.JWT_PREFIX)) {
            String token = authHeader.substring(Constants.JWT_PREFIX.length());
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserId(token);
                Integer role = jwtUtil.getRole(token);
                String authority = resolveAuthority(role);
                if (userId != null && authority != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority(authority))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveAuthority(Integer role) {
        if (Constants.ROLE_STUDENT.equals(role)) {
            return "ROLE_" + Constants.SECURITY_ROLE_STUDENT;
        }
        if (Constants.ROLE_ADMIN.equals(role)) {
            return "ROLE_" + Constants.SECURITY_ROLE_ADMIN;
        }
        return null;
    }
}
