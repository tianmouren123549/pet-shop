package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantLoginRequest;
import com.gzu.petshop.dto.merchant.MerchantRegisterRequest;
import com.gzu.petshop.service.auth.MerchantAuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant/auth")
@CrossOrigin
public class MerchantAuthController {
    private final MerchantAuthService merchantAuthService;

    public MerchantAuthController(MerchantAuthService merchantAuthService) {
        this.merchantAuthService = merchantAuthService;
    }

    @PostMapping("/register")
    public Result<Object> register(@RequestBody MerchantRegisterRequest req) {
        return merchantAuthService.register(req);
    }

    @PostMapping("/login")
    public Result<Object> login(@RequestBody MerchantLoginRequest req) {
        return merchantAuthService.login(req);
    }
}

