package com.shoppulse.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf shells for the UI; it deliberately holds no data access so the pages
 * stay thin clients of the public REST API and this package never couples to other features.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String storefront() {
        return "storefront";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/orders")
    public String orders() {
        return "orders";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/merchant/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
