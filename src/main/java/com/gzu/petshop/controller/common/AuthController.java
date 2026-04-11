package com.gzu.petshop.controller.common;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.LoginRequest;
import com.gzu.petshop.dto.common.RegisterRequest;
import com.gzu.petshop.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<Object> register(@RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public Result<Object> login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }
}

