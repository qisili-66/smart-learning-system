package com.smartlearning.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearning.backend.common.Constants;
import com.smartlearning.backend.common.Result;
import com.smartlearning.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] KNIFE4J_WHITELIST = {
            "/doc.html",
            "/favicon.ico",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Constants.CODE_UNAUTHORIZED, "Token无效或已过期，请重新登录"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        Constants.CODE_FORBIDDEN, "无权限访问该接口"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers(KNIFE4J_WHITELIST).permitAll()
                        .requestMatchers("/admin/**").hasRole(Constants.SECURITY_ROLE_ADMIN)
                        .requestMatchers("/qa/**")
                        .hasAnyRole(Constants.SECURITY_ROLE_STUDENT, Constants.SECURITY_ROLE_ADMIN)
                        .requestMatchers("/user-profiles/**", "/wrong-questions/**", "/study-plans/**",
                                "/assessments/**", "/study-records/**", "/personal-data/**")
                        .hasRole(Constants.SECURITY_ROLE_STUDENT)
                        .requestMatchers("/auth/**", "/users/**", "/learning-resources/**")
                        .hasAnyRole(Constants.SECURITY_ROLE_STUDENT, Constants.SECURITY_ROLE_ADMIN)
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
