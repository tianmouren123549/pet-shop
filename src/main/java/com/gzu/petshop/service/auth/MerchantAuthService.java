package com.gzu.petshop.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantLoginRequest;
import com.gzu.petshop.dto.merchant.MerchantRegisterRequest;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.security.JwtService;
import com.gzu.petshop.service.mail.RegistrationWelcomeMailNotifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class MerchantAuthService {
    private static final Pattern EMAIL_LOOSE =
            Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private final MerchantMapper merchantMapper;
    private final JwtService jwtService;
    private final RegistrationWelcomeMailNotifier registrationWelcomeMailNotifier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MerchantAuthService(
            MerchantMapper merchantMapper,
            JwtService jwtService,
            RegistrationWelcomeMailNotifier registrationWelcomeMailNotifier) {
        this.merchantMapper = merchantMapper;
        this.jwtService = jwtService;
        this.registrationWelcomeMailNotifier = registrationWelcomeMailNotifier;
    }

    /**
     * 商家自助注册：写入 {@code merchant}，密码 bcrypt；成功后返回与登录一致的简要字段供前端展示。
     */
    @Transactional
    public Result<Object> register(MerchantRegisterRequest req) {
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()) {
            return Result.error("商家账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            return Result.error("密码至少 6 位");
        }
        if (req.getShopName() == null || req.getShopName().isBlank()) {
            return Result.error("店铺名称不能为空");
        }
        if (req.getContactName() == null || req.getContactName().isBlank()) {
            return Result.error("联系人不能为空");
        }
        if (req.getPhone() == null || req.getPhone().isBlank()) {
            return Result.error("联系电话不能为空");
        }
        String username = req.getUsername().trim();
        Long dup = merchantMapper.selectCount(new QueryWrapper<Merchant>().eq("username", username));
        if (dup != null && dup > 0) {
            return Result.error("该商家账号已被注册");
        }
        String email = req.getEmail();
        if (email != null) {
            email = email.trim();
            if (email.isBlank()) {
                email = null;
            } else {
                String lower = email.toLowerCase();
                if (!isValidMerchantContactEmail(lower)) {
                    return Result.error("联系邮箱格式不正确");
                }
                email = lower;
            }
        }
        Merchant m = new Merchant();
        m.setUsername(username);
        m.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        m.setShopName(req.getShopName().trim());
        m.setContactName(req.getContactName().trim());
        m.setPhone(req.getPhone().trim());
        m.setEmail(email);
        m.setAvatarUrl(null);
        m.setStatus(1);
        m.setCreatedAt(LocalDateTime.now());
        m.setLastLoginAt(null);
        merchantMapper.insert(m);
        if (email != null && !email.isBlank()) {
            registrationWelcomeMailNotifier.notifyMerchantRegistered(email, m.getShopName(), m.getUsername());
        }
        MerchantLoginResult dto = new MerchantLoginResult(m.getMerchantId(), m.getUsername(), "MERCHANT");
        dto.setToken(jwtService.createAccessToken("MERCHANT", m.getMerchantId()));
        return Result.success(dto);
    }

    private static boolean isValidMerchantContactEmail(String email) {
        if (email == null || email.length() < 5 || email.length() > 128) {
            return false;
        }
        return EMAIL_LOOSE.matcher(email).matches();
    }

    @Transactional
    public Result<Object> login(MerchantLoginRequest req) {
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()) {
            return Result.error("商家账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return Result.error("商家密码不能为空");
        }

        Merchant merchant = merchantMapper.selectOne(
                new QueryWrapper<Merchant>().eq("username", req.getUsername().trim())
        );
        if (merchant == null || merchant.getStatus() == null || merchant.getStatus() == 0) {
            return Result.error("商家账号不存在或已禁用");
        }

        String storedHash = merchant.getPasswordHash();
        String rawPassword = req.getPassword();
        boolean ok;
        try {
            ok = storedHash != null && storedHash.startsWith("$2")
                    ? passwordEncoder.matches(rawPassword, storedHash)
                    : storedHash != null && storedHash.equals(rawPassword);
        } catch (Exception e) {
            ok = storedHash != null && storedHash.equals(rawPassword);
        }
        if (!ok) {
            return Result.error("账号或密码错误");
        }

        merchant.setLastLoginAt(LocalDateTime.now());
        merchantMapper.updateById(merchant);
        MerchantLoginResult dto = new MerchantLoginResult(merchant.getMerchantId(), merchant.getUsername(), "MERCHANT");
        dto.setToken(jwtService.createAccessToken("MERCHANT", merchant.getMerchantId()));
        return Result.success(dto);
    }

    public static class MerchantLoginResult {
        private Long adminId;
        private String username;
        private String role;
        /** JWT 访问令牌 */
        private String token;

        public MerchantLoginResult(Long adminId, String username, String role) {
            this.adminId = adminId;
            this.username = username;
            this.role = role;
        }

        public Long getAdminId() {
            return adminId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}

