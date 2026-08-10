package net.rcetech.clients.service.integration;

import com.google.protobuf.Empty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import tgb.cryptoexchange.grpc.generated.SecurityServiceGrpc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServiceIT extends BaseIntegrationTest {

    private SecurityServiceGrpc.SecurityServiceBlockingStub blockingStub;

    @Value("${secrets.jwt.public}")
    private Resource expectedPublicKeyResource;

    @BeforeEach
    void initStub() {
        blockingStub = SecurityServiceGrpc.newBlockingStub(channel);
    }

    @Test
    @DisplayName("Должен возвращать корректный ключ из конфига")
    void getPublicKey_ShouldReturnConfiguredKey() throws Exception {
        String expectedKeyContent = FileCopyUtils.copyToString(
                new InputStreamReader(expectedPublicKeyResource.getInputStream(), StandardCharsets.UTF_8)
        );

        var request = Empty.getDefaultInstance();
        var response = blockingStub.getPublicKey(request);

        assertThat(response.getJwtKey()).isNotBlank();
        assertThat(response.getJwtKey()).isEqualTo(expectedKeyContent);
    }

}
