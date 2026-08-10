package net.rcetech.clients.service.integration;

import net.rcetech.clients.service.SecurityApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServiceIT extends BaseIntegrationTest {

    @Autowired
    private SecurityApi securityApi;

    @Value("${secrets.jwt.public}")
    private Resource expectedPublicKeyResource;

    @Test
    @DisplayName("Должен возвращать корректный ключ из конфига")
    void getPublicKey_ShouldReturnConfiguredKey() throws Exception {
        String expectedKeyContent = FileCopyUtils.copyToString(
                new InputStreamReader(expectedPublicKeyResource.getInputStream(), StandardCharsets.UTF_8)
        );

        String response = securityApi.getPublicKey();

        assertThat(response).isNotBlank()
                .isEqualTo(expectedKeyContent);
    }

}
