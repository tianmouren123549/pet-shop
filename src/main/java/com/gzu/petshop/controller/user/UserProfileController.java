package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.UserProfileDTO;
import com.gzu.petshop.dto.user.UserProfileUpdateRequest;
import com.gzu.petshop.service.UserProfileService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户资料，与前端 {@code /api/users/{userId}/profile} 一致。
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserProfileController {
    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
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
}
