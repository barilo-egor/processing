package net.rcetech.support.controller;

import net.rcetech.domain.service.support.SupportUserService;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.support.dto.SupportUserResponse;
import net.rcetech.meta.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportUserController.class)
@Import({MetaSecurityConfig.class})
class SupportUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportUserService supportUserService;

    @MockitoBean
    private AuthenticationSuccessHandler successHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9,test1,1790874344130",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012,smokilolik1,1790872344000"
    })
    @DisplayName("Метод должен вернуть JSON представление пользователя поддержки.")
    void get_shouldReturnSupportUserJSON(UUID id, String username, Long millis) throws Exception {
        SupportUserResponse supportUserResponse = new SupportUserResponse(
                id, username, Instant.ofEpochMilli(millis)
        );
        when(supportUserService.findById(id, SupportUserResponse.class)).thenReturn(Optional.of(supportUserResponse));

        mockMvc.perform(get("/api/support/support-user")
                        .with(csrf())
                        .with(user(id.toString()).roles(Role.ADMIN.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.registeredAt").value(millis));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"ADMIN", "OPERATOR"})
    @DisplayName("Метод должен вернуть 403 для не пользователей поддержки.")
    void get_shouldReturn403IfNotSupportUser(Role role) throws Exception {
        mockMvc.perform(get("/api/support/support-user")
                .with(csrf())
                .with(user("c2cbb845-7313-4106-a1a3-e9e4b328bbd9").roles(role.name())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRADER", "GUEST"})
    @DisplayName("Доступ должен быть запрещен для несуществующей роли.")
    void get_shouldReturn403IfUnknownRole(String role) throws Exception {
        mockMvc.perform(get("/api/support/support-user")
                .with(csrf())
                .with(user("c2cbb845-7313-4106-a1a3-e9e4b328bbd9").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9,ADMIN",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012,OPERATOR",
            "0b6f3a52-1d4e-4c8a-9f27-5e3d8c1a7b90,ADMIN;OPERATOR"
    })
    @DisplayName("Метод должен вернуть массив ролей пользователя поддержки.")
    void getRoles_shouldReturnRoles(String id, String roles) throws Exception {
        String[] roleNames = roles.split(";");

        mockMvc.perform(get("/api/support/support-user/roles")
                        .with(csrf())
                        .with(user(id).roles(roleNames)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(roleNames.length)))
                .andExpect(jsonPath("$", containsInAnyOrder((Object[]) roleNames)));
    }

    @ParameterizedTest
    @CsvSource({
            "c2cbb845-7313-4106-a1a3-e9e4b328bbd9,ADMIN;default-roles-processing",
            "fe4a4710-9c2e-4f04-b274-06802fbf2012,OPERATOR;offline_access"
    })
    @DisplayName("Метод не должен возвращать роли, которых нет в Role.")
    void getRoles_shouldIgnoreUnknownRoles(String id, String authorities) throws Exception {
        String[] authorityNames = authorities.split(";");

        mockMvc.perform(get("/api/support/support-user/roles")
                        .with(csrf())
                        .with(user(id).authorities(
                                new SimpleGrantedAuthority("ROLE_" + authorityNames[0]),
                                new SimpleGrantedAuthority(authorityNames[1]),
                                new SimpleGrantedAuthority("ROLE_" + authorityNames[1]))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value(authorityNames[0]));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"ADMIN", "OPERATOR"})
    @DisplayName("Получение ролей должно вернуть 403 для не пользователей поддержки.")
    void getRoles_shouldReturn403IfNotSupportUser(Role role) throws Exception {
        mockMvc.perform(get("/api/support/support-user/roles")
                        .with(csrf())
                        .with(user("c2cbb845-7313-4106-a1a3-e9e4b328bbd9").roles(role.name())))
                .andExpect(status().isForbidden());
    }
}
