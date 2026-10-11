package net.rcetech.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {

    private final String manageAccountURL;

    public RedirectController(@Value("${processing.uri.manage-account}") String manageAccountURL) {
        this.manageAccountURL = manageAccountURL;
    }

    @GetMapping("/")
    public String redirect(HttpServletRequest request) {
        if (request.isUserInRole("ADMIN") || request.isUserInRole("OPERATOR")) {
            return  "redirect:/support/dashboard";
        }
        return "redirect:/client/dashboard";
    }

    @GetMapping("/manage-account")
    public String redirect() {
        return "redirect:" + manageAccountURL;
    }
}
