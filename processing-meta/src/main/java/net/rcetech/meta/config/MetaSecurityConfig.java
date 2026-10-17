package net.rcetech.meta.config;

import net.rcetech.meta.SpringSecurityConfigurer;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.user.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.List;
import java.util.Objects;

@Configuration
@EnableMethodSecurity
@Profile("!disable-security")
public class MetaSecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final boolean secureCookies;

    private final String cookieSameSite;

    public MetaSecurityConfig(ClientRegistrationRepository clientRegistrationRepository,
                              @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
                              @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.secureCookies = secureCookies;
        this.cookieSameSite = cookieSameSite;
    }

    @Bean
    public SecurityFilterChain globalFilterChain(HttpSecurity http,
                                                 List<SpringSecurityConfigurer> configurers,
                                                 @Autowired(required = false)
                                                 AuthenticationSuccessHandler successHandler) {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(WebPath.SUPPORT_API_PATH + "/**")
                .hasAnyRole(Role.ADMIN.name(), Role.OPERATOR.name())
                .requestMatchers(WebPath.CLIENT_API_PATH + "/**")
                .hasRole(Role.CLIENT.name())
                .requestMatchers(WebPath.COMMON_API_PATH + "/**")
                .authenticated()
                .requestMatchers(WebPath.V1_API_PATH + "/**")
                .denyAll()
                .requestMatchers(WebPath.PUBLIC_API_PATH + "/**")
                .permitAll()
                .anyRequest().authenticated()
        );
        for (SpringSecurityConfigurer configurer : configurers) {
            configurer.configure(http);
        }
        if (Objects.nonNull(successHandler)) {
            http.oauth2Login(oAuth2 ->
                    oAuth2.successHandler(successHandler)
            );
        }
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite(cookieSameSite));
        http.csrf(csrf -> csrf
                .spa()
                .csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers(WebPath.PUBLIC_API_PATH + "/**")
        );
        http.logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler()));
        http.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()));
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return oidcLogoutSuccessHandler;
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Role.ADMIN.name()).implies(Role.OPERATOR.name())
                .build();
    }
}
