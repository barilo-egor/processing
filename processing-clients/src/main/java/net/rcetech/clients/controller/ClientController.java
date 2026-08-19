package net.rcetech.clients.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import net.rcetech.meta.clients.dto.AuthRequest;
import net.rcetech.meta.clients.dto.AuthResponse;
import net.rcetech.meta.clients.dto.ClientDTO;
import net.rcetech.meta.clients.dto.TokenPair;
import net.rcetech.clients.exceptions.ClientAlreadyExistsException;
import net.rcetech.clients.exceptions.UnauthorizedException;
import net.rcetech.clients.service.ClientsAuthenticationManagerService;
import net.rcetech.clients.service.ClientProcessService;
import net.rcetech.clients.service.ClientsCookieService;

@RestController
@Slf4j
@RequestMapping("/clients")
public class ClientController {

    private final ClientsCookieService clientsCookieService;

    private final ClientProcessService clientProcessService;

    private final ClientsAuthenticationManagerService authService;

    public ClientController(ClientProcessService clientProcessService, ClientsAuthenticationManagerService authService,
                            ClientsCookieService clientsCookieService) {
        this.clientProcessService = clientProcessService;
        this.authService = authService;
        this.clientsCookieService = clientsCookieService;
    }

    /**
     * Создает нового клиента в системе.
     *
     * @param request объект {@link ClientDTO} с данными клиента.
     * @return {@link ResponseEntity} со статусом 200 (OK) и телом, содержащим данные созданного клиента.
     * @throws ClientAlreadyExistsException, PasswordValidationException со статусом 400 (Bad Request).
     */
    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO request) {
        ClientDTO savedClient = clientProcessService.create(request);
        return ResponseEntity.ok().body(savedClient);
    }

    /**
     * Аутентификация клиента и выдача токенов доступа.
     *
     * @param authRequest данные для входа (логин/пароль или refresh токен)
     * @param response    HTTP-ответ для записи refresh токена в Cookie
     * @return {@link ResponseEntity} с accessToken в теле ответа
     * @throws UnauthorizedException если не переданы учетные данные
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest, HttpServletResponse response) {
        if (authRequest == null || authRequest.username() == null || (authRequest.password() == null
                && authRequest.refreshToken() == null)) {
            throw new UnauthorizedException("No credentials provided");
        }
        TokenPair tokens = authService.authenticate(authRequest);

        response.addHeader(HttpHeaders.SET_COOKIE,
                clientsCookieService.createRefreshTokenCookie(tokens.refreshToken()).toString());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

}
