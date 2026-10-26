package net.rcetech.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.domain.model.clients.ApiKey;
import net.rcetech.domain.model.clients.Client;
import net.rcetech.domain.service.clients.ApiKeyService;
import net.rcetech.domain.service.clients.IpAddressService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.clients.dto.ClientPrincipal;
import net.rcetech.meta.user.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "Api-Key";

    private static final String PROTECTED_URL_PATTERN = WebPath.V1_API_PATH + "/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final ApiKeyService apiKeyService;

    private final IpAddressService ipAddressService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, IpAddressService ipAddressService) {
        this.apiKeyService = apiKeyService;
        this.ipAddressService = ipAddressService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (Objects.isNull(apiKey) || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Optional<ApiKey> apiKeyOpt = apiKeyService.findByKey(apiKey);
        if (apiKeyOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Client client = apiKeyOpt.get().getClient();
        String remoteAddress = request.getRemoteAddr();
        if (!ipAddressService.isAllowed(client, remoteAddress)) {
            log.warn("Запрос клиента {} с IP-адреса {} отклонён: адреса нет в белом списке.",
                    client.getId(), remoteAddress);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var authentication = new ApiKeyAuthenticationToken(
                new ClientPrincipal(client.getId()),
                apiKey,
                List.of(new SimpleGrantedAuthority(Role.CLIENT.withPrefix()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !pathMatcher.match(PROTECTED_URL_PATTERN, path);
    }
}
