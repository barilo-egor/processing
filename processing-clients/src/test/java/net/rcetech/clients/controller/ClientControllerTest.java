package net.rcetech.clients.controller;

import net.rcetech.domain.service.clients.ClientService;
import net.rcetech.meta.clients.ClientStatus;
import net.rcetech.meta.clients.dto.ClientResponseDTO;
import net.rcetech.meta.config.MetaSecurityConfig;
import net.rcetech.meta.config.ProcessingConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({MetaSecurityConfig.class})
@EnableConfigurationProperties(ProcessingConfigurationProperties.class)
class ClientControllerTest {

    @MockitoBean
    private ClientRegistrationRepository  clientRegistrationRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @Test
    void get_shouldReturn500IfClientNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/client")
                        .with(csrf())
                        .with(user("89b4e05b-c129-4ea3-a1d0-088d347b9cd2").roles("CLIENT")))
                .andExpect(status().isInternalServerError());
    }

    @ParameterizedTest
    @CsvSource({
            "89b4e05b-c129-4ea3-a1d0-088d347b9cd2,smokilolik,1790181102840,ACTIVE,http://example.com/callback," +
                    "900,15.5,255695",
            "a2c47cba-7b49-4d9b-961c-31220f3636e4,test1,1790181102840,ACTIVE,http://google.net/callback," +
                    "800,10.0,387"
    })
    void get_shouldReturnClientJson(UUID id, String username, Long registeredAt, ClientStatus status, String callbackUrl,
                                    Integer orderTimeoutSeconds, BigDecimal commissionPercent, Integer balance) throws Exception {
        ClientResponseDTO clientResponseDTO = new ClientResponseDTO(
                id, username, Instant.ofEpochMilli(registeredAt), status, callbackUrl, orderTimeoutSeconds, commissionPercent, balance
        );

        when(clientService.findById(id, ClientResponseDTO.class)).thenReturn(Optional.of(clientResponseDTO));

        mockMvc.perform(get("/api/v1/client")
                        .with(csrf())
                        .with(user(id.toString()).roles("CLIENT"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.registeredAt").value(registeredAt))
                .andExpect(jsonPath("$.status").value(status.name()))
                .andExpect(jsonPath("$.callbackUrl").value(callbackUrl))
                .andExpect(jsonPath("$.orderTimeoutSeconds").value(orderTimeoutSeconds))
                .andExpect(jsonPath("$.commissionPercent").value(commissionPercent.doubleValue()))
                .andExpect(jsonPath("$.balance").value(balance));
    }
}