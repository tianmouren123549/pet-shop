package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.UserAddressDTO;
import com.gzu.petshop.dto.user.UserAddressSaveRequest;
import com.gzu.petshop.security.UserPrincipalUtil;
import com.gzu.petshop.service.user.UserAddressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserAddressController {

    private final UserAddressService userAddressService;

    public UserAddressController(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    @GetMapping("/{userId}/addresses")
    public Result<List<UserAddressDTO>> list(HttpServletRequest request, @PathVariable Long userId) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return authError(deny);
        }
        return Result.success(userAddressService.listByUser(userId));
    }

    @PostMapping("/{userId}/addresses")
    public Result<UserAddressDTO> create(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestBody UserAddressSaveRequest body
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return authError(deny);
        }
        UserAddressDTO dto = userAddressService.create(userId, body);
        return dto == null ? Result.error("收货人与详细地址不能为空") : Result.success(dto);
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    public Result<Void> update(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable Long addressId,
            @RequestBody UserAddressSaveRequest body
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return authError(deny);
        }
        String err = userAddressService.update(userId, addressId, body);
        return err == null ? Result.success() : Result.error(err);
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    public Result<Void> delete(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable Long addressId
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return authError(deny);
        }
        String err = userAddressService.delete(userId, addressId);
        return err == null ? Result.success() : Result.error(err);
    }

    @PutMapping("/{userId}/addresses/{addressId}/default")
    public Result<Void> setDefault(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable Long addressId
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return authError(deny);
        }
        String err = userAddressService.setDefault(userId, addressId);
        return err == null ? Result.success() : Result.error(err);
    }

    private static <T> Result<T> authError(String deny) {
        if (deny != null && deny.contains("登录")) {
            return Result.error(401, deny);
        }
        return Result.error(403, deny != null ? deny : "禁止访问");
    }
}
