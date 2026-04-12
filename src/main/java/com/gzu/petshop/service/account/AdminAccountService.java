package com.gzu.petshop.service.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.admin.AdminMerchantListItemDTO;
import com.gzu.petshop.dto.admin.AdminUserListItemDTO;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.mapper.user.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端：C 端用户与商家账号的查询、启用/禁用、重置密码。
 */
@Service
public class AdminAccountService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAccountService(UserMapper userMapper, MerchantMapper merchantMapper) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
    }

    public List<AdminUserListItemDTO> listUsers() {
        List<User> list = userMapper.selectList(new QueryWrapper<User>().orderByDesc("user_id"));
        List<AdminUserListItemDTO> out = new ArrayList<>();
        for (User u : list) {
            AdminUserListItemDTO row = new AdminUserListItemDTO();
            row.setUserId(u.getUserId());
            row.setNickname(u.getNickname());
            row.setEmail(u.getEmail());
            row.setPhone(u.getPhone());
            row.setStatus(u.getStatus());
            if (u.getCreatedAt() != null) {
                row.setCreatedAt(ISO.format(u.getCreatedAt()));
            }
            out.add(row);
        }
        return out;
    }

    public List<AdminMerchantListItemDTO> listMerchants() {
        List<Merchant> list = merchantMapper.selectList(new QueryWrapper<Merchant>().orderByDesc("merchant_id"));
        List<AdminMerchantListItemDTO> out = new ArrayList<>();
        for (Merchant m : list) {
            AdminMerchantListItemDTO row = new AdminMerchantListItemDTO();
            row.setMerchantId(m.getMerchantId());
            row.setUsername(m.getUsername());
            row.setShopName(m.getShopName());
            row.setContactName(m.getContactName());
            row.setPhone(m.getPhone());
            row.setEmail(m.getEmail());
            row.setStatus(m.getStatus());
            if (m.getCreatedAt() != null) {
                row.setCreatedAt(ISO.format(m.getCreatedAt()));
            }
            if (m.getLastLoginAt() != null) {
                row.setLastLoginAt(ISO.format(m.getLastLoginAt()));
            }
            out.add(row);
        }
        return out;
    }

    @Transactional
    public String updateUserStatus(Long userId, Integer status) {
        if (userId == null || userId <= 0) {
            return "用户ID无效";
        }
        if (status == null || (status != 0 && status != 1)) {
            return "状态须为 0（禁用）或 1（正常）";
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return "用户不存在";
        }
        u.setStatus(status);
        userMapper.updateById(u);
        return null;
    }

    @Transactional
    public String updateMerchantStatus(Long merchantId, Integer status) {
        if (merchantId == null || merchantId <= 0) {
            return "商家ID无效";
        }
        if (status == null || (status != 0 && status != 1)) {
            return "状态须为 0（禁用）或 1（正常）";
        }
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            return "商家不存在";
        }
        m.setStatus(status);
        merchantMapper.updateById(m);
        return null;
    }

    @Transactional
    public String resetUserPassword(Long userId, String newPassword) {
        if (userId == null || userId <= 0) {
            return "用户ID无效";
        }
        if (newPassword == null || newPassword.length() < 6) {
            return "新密码至少 6 位";
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return "用户不存在";
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        userMapper.updateById(u);
        return null;
    }

    @Transactional
    public String resetMerchantPassword(Long merchantId, String newPassword) {
        if (merchantId == null || merchantId <= 0) {
            return "商家ID无效";
        }
        if (newPassword == null || newPassword.length() < 6) {
            return "新密码至少 6 位";
        }
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            return "商家不存在";
        }
        m.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        merchantMapper.updateById(m);
        return null;
    }
}
