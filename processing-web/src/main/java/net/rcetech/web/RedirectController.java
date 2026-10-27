package net.rcetech.web;

import jakarta.servlet.http.HttpServletRequest;
import net.rcetech.meta.user.AdminAccess;
import net.rcetech.meta.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {

    private final String manageAccountURL;

    private final String manageUsersURL;

    public RedirectController(@Value("${processing.uri.manage-account}") String manageAccountURL,
                              @Value("${processing.uri.manage-users}") String manageUsersURL) {
        this.manageAccountURL = manageAccountURL;
        this.manageUsersURL = manageUsersURL;
    }

    @GetMapping("/")
    public String redirect(HttpServletRequest request) {
        if (request.isUserInRole(Role.ADMIN.name()) || request.isUserInRole(Role.OPERATOR.name())) {
            return  "redirect:/support/dashboard";
        }
        if (request.isUserInRole(Role.CLIENT.name())) {
            return "redirect:/client/dashboard";
        }
        return "redirect:/no-role";
    }

    @GetMapping("/manage-account")
    public String redirect() {
        return "redirect:" + manageAccountURL;
    }

    @AdminAccess
    @GetMapping("/manage-users")
    public String redirectToUsers() {
        return "redirect:" + manageUsersURL;
    }
}
