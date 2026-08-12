package net.rcetech.clients.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.clients.exceptions.BaseException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientsJwtServiceTest {

    private final Long accessExpiration = 3600L;

    @Mock
    private Resource secretResource;

    @InjectMocks
    private ClientsJwtService clientsJwtService;

    private PublicKey publicKey;

    private byte[] privateKeyBytes;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        this.publicKey = keyPair.getPublic();
        this.privateKeyBytes = keyPair.getPrivate().getEncoded();

        ReflectionTestUtils.setField(clientsJwtService, "accessExpiration", accessExpiration);
    }

    @Test
    @DisplayName("Успешная генерация JWT-токена со всеми необходимыми клеймами")
    void should_generateValidJwtToken_when_clientDtoProvided() throws IOException {
        ClientDTO clientDto = ClientDTO.builder().id(123L).username("test_client").password(null).build();

        when(secretResource.getContentAsByteArray()).thenReturn(privateKeyBytes);

        String token = clientsJwtService.generateAccessToken(clientDto);

        assertNotNull(token);

        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("123", claims.getSubject());
        assertEquals("api-clients", claims.getIssuer());
        assertNotNull(claims.getId());
        assertDoesNotThrow(() -> UUID.fromString(claims.getId()));

        assertEquals("test_client", claims.get("username", String.class));
        assertEquals("CLIENT", claims.get("role", String.class));

        Date expiration = claims.getExpiration();
        long diffInSeconds = (expiration.getTime() - new Date().getTime()) / 1000;
        assertTrue(diffInSeconds > 0 && diffInSeconds <= accessExpiration);
    }

    @Test
    @DisplayName("Бросает BaseException, если не удалось прочитать или распарсить приватный ключ")
    void should_throwBaseException_when_privateKeyIsInvalid() throws IOException {
        ClientDTO clientDto = ClientDTO.builder().id(123L).username("test_client").password(null).build();

        when(secretResource.getContentAsByteArray()).thenReturn(new byte[] { 1, 2, 3, 4, 5 });

        BaseException exception = assertThrows(BaseException.class, () ->
                clientsJwtService.generateAccessToken(clientDto)
        );

        assertEquals("Failed to load private key", exception.getMessage());
        assertNotNull(exception.getMessage());
    }

}
