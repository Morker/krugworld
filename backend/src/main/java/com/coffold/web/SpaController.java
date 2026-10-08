package com.coffold.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app routes in the browser (/ledger, /quests, ...). When someone
 * opens or refreshes one of those URLs directly, the server has no such file,
 * so it hands back the app's index.html and React Router takes it from there.
 */
@Controller
public class SpaController {

    @GetMapping({"/ledger", "/budgets", "/quests", "/recurring", "/accounts", "/settings", "/login", "/register"})
    public String app() {
        return "forward:/index.html";
    }
}
