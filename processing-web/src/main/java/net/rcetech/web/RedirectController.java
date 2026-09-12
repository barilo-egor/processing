package net.rcetech.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {

    @GetMapping("/")
    public String redirect(HttpServletRequest request) {
        if (request.isUserInRole("ADMIN") || request.isUserInRole("OPERATOR")) {
            return  "redirect:/support/dashboard";
        }
        return "redirect:/dashboard";
    }
}
