package tgb.cryptoexchange.clientsapi.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tgb.cryptoexchange.clientsapi.dto.AuthRequest;
import tgb.cryptoexchange.clientsapi.dto.AuthResponse;
import tgb.cryptoexchange.clientsapi.dto.ClientDTO;
import tgb.cryptoexchange.clientsapi.dto.TokenPair;
import tgb.cryptoexchange.clientsapi.exceptions.ClientAlreadyExistsException;
import tgb.cryptoexchange.clientsapi.exceptions.UnauthorizedException;
import tgb.cryptoexchange.clientsapi.service.AuthenticationManagerService;
import tgb.cryptoexchange.clientsapi.service.ClientService;
import tgb.cryptoexchange.clientsapi.service.CookieService;

@RestController
@Slf4j
@RequestMapping("/api-clients")
public class ClientController {

    private final CookieService cookieService;

    private final ClientService clientService;

    private final AuthenticationManagerService authService;

    public ClientController(ClientService clientService, AuthenticationManagerService authService,
            CookieService cookieService) {
        this.clientService = clientService;
        this.authService = authService;
        this.cookieService = cookieService;
    }

    /**
     * Создает нового клиента в системе.
     *
     * @param request объект {@link ClientDTO} с данными клиента.
     * @return {@link ResponseEntity} со статусом 200 (OK) и телом, содержащим данные созданного клиента.
     * @throws ClientAlreadyExistsException, PasswordValidationException со статусом 400 (Bad Request).
     */
    @PostMapping("/clients")
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO request) {
        ClientDTO savedClient = clientService.create(request);
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
                cookieService.createRefreshTokenCookie(tokens.refreshToken()).toString());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

}
