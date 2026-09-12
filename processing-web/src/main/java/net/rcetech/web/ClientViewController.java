package net.rcetech.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class ClientViewController {

    @GetMapping("/dashboard")
    public String view() {
        return "forward:/client/index.html";
    }
}
