package net.rcetech.clients.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.rcetech.clients.exceptions.BaseException;
import net.rcetech.meta.clients.service.SecurityApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class SecurityApiImpl implements SecurityApi {

    private final Resource jwtPublicKeyResource;

    private String jwtPublicKeyString;

    public SecurityApiImpl(@Value("${secrets.jwt.public}") Resource jwtPublicKeyResource) {
        this.jwtPublicKeyResource = jwtPublicKeyResource;
    }

    @PostConstruct
    public void init() {
        try (Reader reader = new InputStreamReader(jwtPublicKeyResource.getInputStream(), StandardCharsets.UTF_8)) {
            this.jwtPublicKeyString = FileCopyUtils.copyToString(reader);
            log.info("JWT Public key successfully loaded from file.");
        } catch (Exception e) {
            log.error("Failed to read JWT public key file", e);
            throw new BaseException("Critical error: public key file is unreadable");
        }
    }

    @Override
    public String getPublicKey() {
        return jwtPublicKeyString;
    }

}
