package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.FrequentProductItemDTO;
import com.gzu.petshop.dto.user.UserProfileDTO;
import com.gzu.petshop.dto.user.UserProfileUpdateRequest;
import com.gzu.petshop.security.UserPrincipalUtil;
import com.gzu.petshop.service.account.UserProfileService;
import com.gzu.petshop.service.user.UserProductFrequencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户资料，与前端 {@code /api/users/{userId}/profile} 一致。
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserProfileController {
    private final UserProfileService userProfileService;
    private final UserProductFrequencyService userProductFrequencyService;

    public UserProfileController(
            UserProfileService userProfileService,
            UserProductFrequencyService userProductFrequencyService
    ) {
        this.userProfileService = userProfileService;
        this.userProductFrequencyService = userProductFrequencyService;
    }

    @GetMapping("/{userId}/profile")
    public Result<UserProfileDTO> getProfile(@PathVariable Long userId) {
        UserProfileDTO dto = userProfileService.getProfile(userId);
        return dto == null ? Result.error("用户不存在") : Result.success(dto);
    }

    @PutMapping("/{userId}/profile")
    public Result<Void> updateProfile(@PathVariable Long userId, @RequestBody UserProfileUpdateRequest req) {
        String err = userProfileService.updateProfile(userId, req);
        return err == null ? Result.success() : Result.error(err);
    }

    @GetMapping("/{userId}/pet-preference")
    public Result<Map<String, String>> getPetPreference(@PathVariable Long userId) {
        String pref = userProfileService.getPetPreference(userId);
        if (pref == null) {
            // 用户存在但未设置时也返回 success，前端据此弹首次引导。
            UserProfileDTO dto = userProfileService.getProfile(userId);
            if (dto == null) {
                return Result.error("用户不存在");
            }
            Map<String, String> data = new HashMap<>();
            data.put("petPreference", null);
            return Result.success(data);
        }
        Map<String, String> data = new HashMap<>();
        data.put("petPreference", pref);
        return Result.success(data);
    }

    /**
     * 常购清单（确认收货后累计），按购买件数排序。
     */
    @GetMapping("/{userId}/frequent-products")
    public Result<List<FrequentProductItemDTO>> frequentProducts(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        return Result.success(userProductFrequencyService.topByUser(userId, limit));
    }

    @PutMapping("/{userId}/pet-preference")
    public Result<Void> updatePetPreference(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        String pref = body != null && body.get("petPreference") != null
                ? String.valueOf(body.get("petPreference"))
                : null;
        String err = userProfileService.updatePetPreference(userId, pref);
        return err == null ? Result.success() : Result.error(err);
    }
}
