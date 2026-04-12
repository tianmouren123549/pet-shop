package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantProfileUpdateRequest;
import com.gzu.petshop.service.account.MerchantProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant/profile")
@CrossOrigin
public class MerchantProfileController {
    private final MerchantProfileService merchantProfileService;

    public MerchantProfileController(MerchantProfileService merchantProfileService) {
        this.merchantProfileService = merchantProfileService;
    }

    @GetMapping("/{merchantId}")
    public Result<Object> getProfile(@PathVariable Long merchantId) {
        var profile = merchantProfileService.getProfile(merchantId);
        return profile == null ? Result.error("商家不存在") : Result.success(profile);
    }

    @PutMapping("/{merchantId}")
    public Result<Void> updateProfile(@PathVariable Long merchantId, @RequestBody MerchantProfileUpdateRequest req) {
        String err = merchantProfileService.updateProfile(merchantId, req);
        return err == null ? Result.success() : Result.error(err);
    }
}

