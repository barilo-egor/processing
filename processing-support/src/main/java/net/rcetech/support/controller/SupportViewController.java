package net.rcetech.support.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/support/dashboard")
public class SupportViewController {

    @GetMapping
    public String view() {
        return "forward:/support/index.html";
    }
}
