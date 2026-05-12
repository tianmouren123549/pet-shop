package com.gzu.petshop.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gzu.petshop.dto.user.UserAddressDTO;
import com.gzu.petshop.dto.user.UserAddressSaveRequest;
import com.gzu.petshop.entity.UserAddress;
import com.gzu.petshop.mapper.user.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserAddressService {

    private final UserAddressMapper userAddressMapper;

    public UserAddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }

    public List<UserAddressDTO> listByUser(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<UserAddress> rows = userAddressMapper.selectList(
                new QueryWrapper<UserAddress>().eq("user_id", userId).orderByDesc("is_default").orderByDesc("updated_at"));
        return rows.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public UserAddressDTO create(Long userId, UserAddressSaveRequest req) {
        if (req == null || blank(req.getReceiverName()) || blank(req.getReceiverDetail())) {
            return null;
        }
        UserAddress e = new UserAddress();
        e.setUserId(userId);
        e.setLabel(trimNull(req.getLabel()));
        e.setReceiverName(req.getReceiverName().trim());
        e.setReceiverPhone(blankToEmpty(req.getReceiverPhone()));
        e.setReceiverRegion(blankToEmpty(req.getReceiverRegion()));
        e.setReceiverDetail(req.getReceiverDetail().trim());
        int def = req.getIsDefault() != null && req.getIsDefault() == 1 ? 1 : 0;
        if (def == 1) {
            clearDefault(userId);
        } else if (countByUser(userId) == 0) {
            def = 1;
        }
        e.setIsDefault(def);
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        userAddressMapper.insert(e);
        return toDto(e);
    }

    @Transactional
    public String update(Long userId, Long addressId, UserAddressSaveRequest req) {
        UserAddress e = userAddressMapper.selectById(addressId);
        if (e == null || !userId.equals(e.getUserId())) {
            return "地址不存在";
        }
        if (req == null || blank(req.getReceiverName()) || blank(req.getReceiverDetail())) {
            return "收货人与详细地址不能为空";
        }
        e.setLabel(trimNull(req.getLabel()));
        e.setReceiverName(req.getReceiverName().trim());
        e.setReceiverPhone(blankToEmpty(req.getReceiverPhone()));
        e.setReceiverRegion(blankToEmpty(req.getReceiverRegion()));
        e.setReceiverDetail(req.getReceiverDetail().trim());
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearDefault(userId);
            e.setIsDefault(1);
        }
        e.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.updateById(e);
        return null;
    }

    @Transactional
    public String delete(Long userId, Long addressId) {
        UserAddress e = userAddressMapper.selectById(addressId);
        if (e == null || !userId.equals(e.getUserId())) {
            return "地址不存在";
        }
        boolean wasDefault = e.getIsDefault() != null && e.getIsDefault() == 1;
        userAddressMapper.deleteById(addressId);
        if (wasDefault) {
            List<UserAddress> rest = userAddressMapper.selectList(
                    new QueryWrapper<UserAddress>().eq("user_id", userId).orderByDesc("updated_at").last("LIMIT 1"));
            if (!rest.isEmpty()) {
                UserAddress first = rest.get(0);
                first.setIsDefault(1);
                first.setUpdatedAt(LocalDateTime.now());
                userAddressMapper.updateById(first);
            }
        }
        return null;
    }

    @Transactional
    public String setDefault(Long userId, Long addressId) {
        UserAddress e = userAddressMapper.selectById(addressId);
        if (e == null || !userId.equals(e.getUserId())) {
            return "地址不存在";
        }
        clearDefault(userId);
        e.setIsDefault(1);
        e.setUpdatedAt(LocalDateTime.now());
        userAddressMapper.updateById(e);
        return null;
    }

    private void clearDefault(Long userId) {
        UserAddress patch = new UserAddress();
        patch.setIsDefault(0);
        userAddressMapper.update(patch, new UpdateWrapper<UserAddress>().eq("user_id", userId));
    }

    private long countByUser(Long userId) {
        return userAddressMapper.selectCount(new QueryWrapper<UserAddress>().eq("user_id", userId));
    }

    private UserAddressDTO toDto(UserAddress e) {
        UserAddressDTO d = new UserAddressDTO();
        d.setAddressId(e.getAddressId());
        d.setUserId(e.getUserId());
        d.setLabel(e.getLabel());
        d.setReceiverName(e.getReceiverName());
        d.setReceiverPhone(e.getReceiverPhone());
        d.setReceiverRegion(e.getReceiverRegion());
        d.setReceiverDetail(e.getReceiverDetail());
        d.setIsDefault(e.getIsDefault());
        return d;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
