package net.rcetech.support.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/support/dashboard")
public class SupportViewController {

    @GetMapping
    public String view(HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN") || !request.isUserInRole("OPERATOR")) {
            throw new AuthorizationDeniedException("Access denied");
        }
        return "forward:/support/index.html";
    }
}
