package net.rcetech.clients.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.clients.exceptions.BaseException;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

// TODO grewgh "java.time" classes should be used for dates and times
@Service
@Slf4j
public class ClientsJwtService {

    private final Resource secret;

    private final Long accessExpiration;

    private final String jwtVersion;

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public ClientsJwtService(@Value("${secrets.jwt.private}") Resource secret,
                             @Value("${secrets.jwt.ttl-seconds}") Long accessExpiration,
                             @Value("${secrets.jwt.version}") String jwtVersion) {
        this.secret = secret;
        this.accessExpiration = accessExpiration;
        this.jwtVersion = jwtVersion;
    }

    /**
     * Генерирует подписанный Access-токен на основе данных клиента.
     * Токен подписывается приватным ключом по алгоритму RS256 и содержит
     * идентификатор клиента (subject), его имя пользователя (username) и роль.
     *
     * @param clientDTO данные клиента для авторизационного контекста
     * @return строка сгенерированного и подписанного JWT-токена
     */
    public String generateAccessToken(ClientDTO clientDTO) {
        Instant now = Instant.now();

        return Jwts.builder()
                .header()
                .keyId(jwtVersion).and()
                .subject(clientDTO.getId().toString())
                .issuer("api-clients")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpiration)))
                .id(generator.generate().toString())
                .claim("username", clientDTO.getUsername())
                .claim("role", "CLIENT")
                .claim("ordexp", clientDTO.getOrderTimeoutSeconds())
                .signWith(getPrivateKey())
                .compact();
    }

    private PrivateKey getPrivateKey() {
        try {
            byte[] keyBytes = secret.getContentAsByteArray();
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new BaseException("Failed to load private key");
        }
    }

}
