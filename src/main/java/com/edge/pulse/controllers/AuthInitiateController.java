package com.edge.pulse.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Controller
@Profile("azure")   // /api/auth/login → redirects to the Azure OAuth2 endpoint; not used on k2 (X4Auth login).
@RequestMapping("/api/auth")
public class AuthInitiateController {
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        log.debug("Initiating OAuth2 login flow");
        response.sendRedirect("/oauth2/authorization/azure");
    }
}
