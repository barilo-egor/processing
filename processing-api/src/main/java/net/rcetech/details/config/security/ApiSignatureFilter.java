package net.rcetech.details.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import net.rcetech.details.dto.ClientApiErrorResponse;
import net.rcetech.details.dto.ClientByApiKeyDTO;
import net.rcetech.details.enums.ClientStatus;
import net.rcetech.details.exceptions.BaseException;
import net.rcetech.details.exceptions.ClientNotFoundException;
import net.rcetech.details.exceptions.InvalidApiKeyException;
import net.rcetech.details.service.ClientAuthService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
public class ApiSignatureFilter extends OncePerRequestFilter {

    private final ClientAuthService clientAuthService;

    private final ObjectMapper objectMapper;

    public ApiSignatureFilter(ClientAuthService clientAuthService, ObjectMapper objectMapper) {
        this.clientAuthService = clientAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String INVALID_SIGNATURE = "Invalid signature";
        final String SIGNATURE_NOT_MATCH = "The provided signature does not match.";
        String authHeader = request.getHeader("Authorization");
        String signatureHeader = request.getHeader("Signature");
        String timestampHeader = request.getHeader("X-Timestamp");

        if (authHeader == null || !authHeader.startsWith("Api-Key ")) {
            sendJsonError(response, HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase());
            return;
        }
        if (signatureHeader == null || timestampHeader == null) {
            sendJsonError(response, HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE,
                    SIGNATURE_NOT_MATCH);
            return;
        }

        String apiKey = authHeader.substring(8).trim();

        ClientByApiKeyDTO client;
        try {
            client = clientAuthService.getClientByApiKey(apiKey);
            if (client == null) {
                sendJsonError(response, HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase());
                return;
            }
        } catch (ClientNotFoundException | InvalidApiKeyException ex) {
            log.warn("Authentication failed due to client status: {}", ex.getMessage());
            sendJsonError(response, HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase());
            return;
        } catch (Exception ex) {
            log.error("Internal error during client authentication", ex);
            sendJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                    "An unexpected error occurred.");
            return;
        }
        if (ClientStatus.BLOCKED.equals(client.getStatus())) {
            log.warn("Access denied: client '{}' is blocked", client.getUsername());
            sendJsonError(response, HttpStatus.FORBIDDEN, "Forbidden", "User blocked.");
            return;
        }

        try {
            long timestamp = Long.parseLong(timestampHeader);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > 300) {
                sendJsonError(response, HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE,
                        SIGNATURE_NOT_MATCH);
                return;
            }
        } catch (NumberFormatException e) {
            sendJsonError(response, HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE,
                    SIGNATURE_NOT_MATCH);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String method = cachedRequest.getMethod().toUpperCase();
        String path = cachedRequest.getRequestURI();
        String content = "";

        if (List.of("POST", "PUT", "PATCH").contains(method.toUpperCase())) {
            content = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);
        }

        String dataToSign = String.format("%s|%s|%s|%s", method, path, timestampHeader, content);
        String expectedSignature;
        try {
            expectedSignature = hmacSha256(dataToSign, client.getSecret());
        } catch (BaseException e) {
            log.error("Crypto verification failed due to internal error", e);
            sendJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Crypto error.");
            return;
        }

        if (!expectedSignature.equalsIgnoreCase(signatureHeader)) {
            sendJsonError(response, HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE,
                    SIGNATURE_NOT_MATCH);
            return;
        }

        cachedRequest.setAttribute("authenticatedClient", client);
        filterChain.doFilter(cachedRequest, response);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC algorithm not available", e);
            throw new BaseException("Failed to generate data signature.");
        }
    }

    private void sendJsonError(HttpServletResponse response, HttpStatus httpStatus, String title, String detail)
            throws IOException {
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ClientApiErrorResponse error = new ClientApiErrorResponse(title, httpStatus.value(), detail);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

}
