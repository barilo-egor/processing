package net.rcetech.support.controller;

import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.support.dto.MerchantHistoryResponse;
import net.rcetech.meta.user.Role;
import net.rcetech.support.service.MerchantHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantHistoryController.class)
@Import({ MetaSecurityConfig.class })
class MerchantHistoryControllerTest {

    @MockitoBean
    private MerchantHistoryService merchantHistoryService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(merchantHistoryService.getHistory(any(), any())).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("Метод должен вызвать сервис истории для роли ADMIN")
    void get_shouldCallServiceForAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-history")
                        .with(user(adminId.toString()).roles(Role.ADMIN.name()))
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantHistoryService).getHistory(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Метод должен вызвать сервис истории для роли OPERATOR")
    void get_shouldCallServiceForOperator() throws Exception {
        UUID operatorId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-history")
                        .with(user(operatorId.toString()).roles(Role.OPERATOR.name()))
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(merchantHistoryService).getHistory(any(), any(Pageable.class));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"ADMIN", "OPERATOR"})
    @DisplayName("Метод должен вернуть статус 403, если запрос выполняется не ADMIN и не OPERATOR")
    void get_shouldReturn403ForForbiddenRoles(Role role) throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-history")
                        .with(user(userId.toString()).roles(role.name()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "GUEST"})
    @DisplayName("Доступ должен быть запрещен для несуществующей роли.")
    void get_shouldReturn403IfUnknownRole(String role) throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/api/support/merchant-history")
                        .with(user(userId.toString()).roles(role))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "0,20,20,45,3",
            "2,20,5,45,3",
            "1,10,10,100,10",
            "3,5,0,15,3"
    })
    @DisplayName("Метод должен передать в сервис запрошенную страницу и вернуть total и число страниц из ответа сервиса.")
    void get_shouldReturnPageMetadataFromService(int pageNumber, int pageSize, int count, long total,
                                                  int totalPages) throws Exception {
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);
        List<MerchantHistoryResponse> content = IntStream.range(0, count)
                .mapToObj(i -> new MerchantHistoryResponse("op-" + i, null, null, null, null, null, null, null,
                        null, null))
                .toList();
        when(merchantHistoryService.getHistory(any(), eq(pageable))).thenReturn(new PageImpl<>(content, pageable, total));

        mockMvc.perform(get("/api/support/merchant-history")
                        .param("page", String.valueOf(pageNumber))
                        .param("size", String.valueOf(pageSize))
                        .with(user(UUID.randomUUID().toString()).roles(Role.OPERATOR.name()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(count))
                .andExpect(jsonPath("$.page.number").value(pageNumber))
                .andExpect(jsonPath("$.page.size").value(pageSize))
                .andExpect(jsonPath("$.page.totalElements").value(total))
                .andExpect(jsonPath("$.page.totalPages").value(totalPages));
    }
}
