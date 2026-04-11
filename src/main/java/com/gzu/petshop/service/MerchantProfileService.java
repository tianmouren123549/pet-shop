package com.gzu.petshop.service;

import com.gzu.petshop.dto.merchant.MerchantProfileUpdateRequest;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.mapper.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家资料读写（与前端 {@code merchantGetProfile} / {@code merchantUpdateProfile} 一致）。
 */
@Service
public class MerchantProfileService {
    private final MerchantMapper merchantMapper;

    public MerchantProfileService(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    public MerchantProfileView getProfile(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return null;
        }
        return new MerchantProfileView(
                merchant.getMerchantId(),
                merchant.getUsername(),
                safe(merchant.getShopName()),
                safe(merchant.getContactName()),
                safe(merchant.getPhone()),
                safe(merchant.getEmail()),
                merchant.getAvatarUrl() != null ? merchant.getAvatarUrl() : ""
        );
    }

    @Transactional
    public boolean updateProfile(Long merchantId, MerchantProfileUpdateRequest req) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return false;
        }
        if (req != null) {
            merchant.setShopName(safe(req.getShopName()));
            merchant.setContactName(safe(req.getContactName()));
            merchant.setPhone(safe(req.getPhone()));
            merchant.setEmail(safe(req.getEmail()));
            if (req.getAvatarUrl() != null) {
                String av = req.getAvatarUrl().trim();
                merchant.setAvatarUrl(av.isEmpty() ? null : av);
            }
        }
        return merchantMapper.updateById(merchant) > 0;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    public static class MerchantProfileView {
        private Long merchantId;
        private String username;
        private String shopName;
        private String contactName;
        private String phone;
        private String email;
        private String avatarUrl;

        public MerchantProfileView(Long merchantId, String username, String shopName, String contactName, String phone, String email, String avatarUrl) {
            this.merchantId = merchantId;
            this.username = username;
            this.shopName = shopName;
            this.contactName = contactName;
            this.phone = phone;
            this.email = email;
            this.avatarUrl = avatarUrl;
        }

        public Long getMerchantId() {
            return merchantId;
        }

        public String getUsername() {
            return username;
        }

        public String getShopName() {
            return shopName;
        }

        public String getContactName() {
            return contactName;
        }

        public String getPhone() {
            return phone;
        }

        public String getEmail() {
            return email;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }
}

