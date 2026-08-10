package net.rcetech.support.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import net.rcetech.support.dto.UserDTO;
import net.rcetech.support.exceptions.BaseException;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

@Service
@Slf4j
public class JwtService {

    private final Resource secret;

    private final Long accessExpiration;

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    private final String jwtVersion;

    public JwtService(@Value("${secrets.jwt.private}") Resource secret,
            @Value("${secrets.jwt.ttl-seconds}") Long accessExpiration,
            @Value("${secrets.jwt.version}") String jwtVersion) {
        this.secret = secret;
        this.accessExpiration = accessExpiration;
        this.jwtVersion = jwtVersion;
    }

    /**
     * Генерирует подписанный Access-токен на основе данных пользователя.
     * Токен подписывается приватным ключом по алгоритму RS256 и содержит
     * идентификатор пользователя (subject), его имя пользователя (username) и роль.
     *
     * @param userDTO данные пользователя для авторизационного контекста
     * @return строка сгенерированного и подписанного JWT-токена
     */
    public String generateAccessToken(UserDTO userDTO) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(jwtVersion).and()
                .subject(userDTO.getId().toString())
                .issuer("support-users")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpiration)))
                .id(generator.generate().toString())
                .claim("role", userDTO.getRole())
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
