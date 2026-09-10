package net.rcetech.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientPrincipal;
import net.rcetech.meta.config.security.ApiKeyAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "Api-Key";

    private static final String PROTECTED_URL_PATTERN = WebPath.PUBLIC_API_PATH_V1 + "/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            Optional<ApiKey> apiKeyOpt = apiKeyService.findByKey(apiKey);
            if (apiKeyOpt.isPresent()) {
                ApiKey clientKey = apiKeyOpt.get();
                var authentication = new ApiKeyAuthenticationToken(
                        new ClientPrincipal(clientKey.getClient().getId()),
                        apiKey,
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !pathMatcher.match(PROTECTED_URL_PATTERN, path);
    }
}
