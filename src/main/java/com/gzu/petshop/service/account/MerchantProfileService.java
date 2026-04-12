package com.gzu.petshop.service.account;

import com.gzu.petshop.dto.merchant.MerchantProfileUpdateRequest;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        String targetWeekly =
                merchant.getSalesTargetWeekly() != null
                        ? merchant.getSalesTargetWeekly().setScale(2, RoundingMode.HALF_UP).toPlainString()
                        : "";
        return new MerchantProfileView(
                merchant.getMerchantId(),
                merchant.getUsername(),
                safe(merchant.getShopName()),
                safe(merchant.getContactName()),
                safe(merchant.getPhone()),
                safe(merchant.getEmail()),
                merchant.getAvatarUrl() != null ? merchant.getAvatarUrl() : "",
                targetWeekly
        );
    }

    /**
     * 更新商家资料。
     *
     * @return {@code null} 表示成功；否则为错误文案
     */
    @Transactional
    public String updateProfile(Long merchantId, MerchantProfileUpdateRequest req) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return "商家不存在";
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
            if (req.getSalesTargetWeekly() != null) {
                String raw = req.getSalesTargetWeekly().trim();
                if (raw.isEmpty()) {
                    merchant.setSalesTargetWeekly(null);
                } else {
                    try {
                        BigDecimal v = new BigDecimal(raw);
                        if (v.signum() < 0) {
                            return "销售额目标不能为负数";
                        }
                        merchant.setSalesTargetWeekly(v.setScale(2, RoundingMode.HALF_UP));
                    } catch (NumberFormatException ex) {
                        return "销售额目标须为有效数字";
                    }
                }
            }
        }
        return merchantMapper.updateById(merchant) > 0 ? null : "保存失败";
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
        /** 每周销售额目标（元），两位小数；空串表示未设置 */
        private String salesTargetWeekly;

        public MerchantProfileView(
                Long merchantId,
                String username,
                String shopName,
                String contactName,
                String phone,
                String email,
                String avatarUrl,
                String salesTargetWeekly) {
            this.merchantId = merchantId;
            this.username = username;
            this.shopName = shopName;
            this.contactName = contactName;
            this.phone = phone;
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.salesTargetWeekly = salesTargetWeekly;
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

        public String getSalesTargetWeekly() {
            return salesTargetWeekly;
        }
    }
}

