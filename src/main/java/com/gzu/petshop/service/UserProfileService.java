package com.gzu.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.user.UserProfileDTO;
import com.gzu.petshop.dto.user.UserProfileUpdateRequest;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 用户资料（表 {@code user}，含 {@code avatar_url}）；登录邮箱可修改但须保持唯一。
 */
@Service
public class UserProfileService {
    private static final Pattern EMAIL_LOOSE =
            Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");

    private final UserMapper userMapper;

    public UserProfileService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserProfileDTO getProfile(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        UserProfileDTO d = new UserProfileDTO();
        d.setUserId(u.getUserId());
        d.setNickname(u.getNickname());
        d.setPhone(u.getPhone() != null ? u.getPhone() : "");
        d.setEmail(u.getEmail() != null ? u.getEmail() : "");
        d.setAvatarUrl(u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        return d;
    }

    /**
     * @return 错误文案，成功返回 {@code null}
     */
    @Transactional
    public String updateProfile(Long userId, UserProfileUpdateRequest req) {
        if (req == null) {
            return "参数无效";
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return "用户不存在";
        }
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();
        String email = req.getEmail() == null ? "" : req.getEmail().trim().toLowerCase();
        if (nickname.isEmpty()) {
            return "昵称不能为空";
        }
        if (email.isEmpty()) {
            return "邮箱不能为空";
        }
        if (!isValidEmail(email)) {
            return "邮箱格式不正确";
        }
        User emailDup = userMapper.selectOne(
                new QueryWrapper<User>().eq("email", email).ne("user_id", userId));
        if (emailDup != null) {
            return "该邮箱已被使用";
        }

        String phone = req.getPhone() == null ? "" : req.getPhone().trim();
        if (!phone.isEmpty()) {
            User phoneDup = userMapper.selectOne(
                    new QueryWrapper<User>().eq("phone", phone).ne("user_id", userId));
            if (phoneDup != null) {
                return "该手机号已被使用";
            }
        }

        u.setNickname(nickname);
        u.setEmail(email);
        u.setPhone(phone.isEmpty() ? null : phone);
        if (req.getAvatarUrl() != null) {
            String av = req.getAvatarUrl().trim();
            u.setAvatarUrl(av.isEmpty() ? null : av);
        }
        userMapper.updateById(u);
        return null;
    }

    private static boolean isValidEmail(String email) {
        if (email.length() < 5 || email.length() > 128) {
            return false;
        }
        return EMAIL_LOOSE.matcher(email).matches();
    }
}
