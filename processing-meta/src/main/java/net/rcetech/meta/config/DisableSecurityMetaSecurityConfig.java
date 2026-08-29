package net.rcetech.meta.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Configuration
@Profile("disable-security")
@Slf4j
public class DisableSecurityMetaSecurityConfig {

    @Bean
    public SecurityFilterChain globalDevFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
        );
        http.csrf(AbstractHttpConfigurer::disable);
        http.addFilterBefore(new MockUserFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static class MockUserFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull FilterChain filterChain) throws ServletException, IOException {
            String testUserHeader = request.getHeader("Test-User");
            if (Objects.nonNull(testUserHeader)) {
                log.debug("Получен тестовый пользователь в заголовке Test-User: {}", testUserHeader);
                try {
                    String[] userDetails = testUserHeader.split(";");
                    User mockUser = new User(userDetails[0], "", List.of(new SimpleGrantedAuthority(userDetails[1])));
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.debug("Ошибка при попытке установить в контекст тестового пользователя: {}", e.getMessage(), e);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
