package com.gzu.petshop.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.LoginRequest;
import com.gzu.petshop.dto.common.RegisterRequest;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.user.UserMapper;
import com.gzu.petshop.security.JwtService;
import com.gzu.petshop.service.mail.RegistrationWelcomeMailNotifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern EMAIL_LOOSE =
            Pattern.compile("^[\\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RegistrationWelcomeMailNotifier registrationWelcomeMailNotifier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            UserMapper userMapper,
            JwtService jwtService,
            RegistrationWelcomeMailNotifier registrationWelcomeMailNotifier) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.registrationWelcomeMailNotifier = registrationWelcomeMailNotifier;
    }

    @Transactional
    public Result<Object> register(RegisterRequest req) {
        if (req == null || req.getEmail() == null || req.getEmail().isBlank()) {
            return Result.error("邮箱不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            return Result.error("密码至少 6 位");
        }
        String email = req.getEmail().trim().toLowerCase();
        if (!isValidEmail(email)) {
            return Result.error("邮箱格式不正确");
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null) {
            return Result.error("该邮箱已被注册");
        }

        String phone = req.getPhone();
        if (phone != null) {
            phone = phone.trim();
            if (phone.isBlank()) {
                phone = null;
            }
        }

        User user = new User();
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? "用户" : req.getNickname().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());

        userMapper.insert(user);

        registrationWelcomeMailNotifier.notifyUserRegistered(email, user.getNickname());

        AuthResult ar = new AuthResult(user.getUserId(), user.getNickname());
        ar.setToken(jwtService.createAccessToken("USER", user.getUserId()));
        return Result.success(ar);
    }

    public Result<Object> login(LoginRequest req) {
        if (req == null || req.getEmail() == null || req.getEmail().isBlank()) {
            return Result.error("邮箱不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return Result.error("密码不能为空");
        }

        String email = req.getEmail().trim().toLowerCase();
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            return Result.error("账号不存在或已禁用");
        }

        String storedHash = user.getPasswordHash();
        String rawPassword = req.getPassword();
        boolean ok;
        try {
            if (storedHash != null && storedHash.startsWith("$2")) {
                ok = passwordEncoder.matches(rawPassword, storedHash);
            } else {
                ok = storedHash != null && storedHash.equals(rawPassword);
            }
        } catch (Exception e) {
            ok = storedHash != null && storedHash.equals(rawPassword);
        }

        if (!ok) {
            return Result.error("邮箱或密码错误");
        }

        AuthResult ar = new AuthResult(user.getUserId(), user.getNickname());
        ar.setToken(jwtService.createAccessToken("USER", user.getUserId()));
        return Result.success(ar);
    }

    private static boolean isValidEmail(String email) {
        if (email == null || email.length() < 5 || email.length() > 128) {
            return false;
        }
        return EMAIL_LOOSE.matcher(email).matches();
    }

    /**
     * 返回给前端的简化登录信息。
     */
    public static class AuthResult {
        private Long userId;
        private String nickname;
        /** JWT 访问令牌，请求头 {@code Authorization: Bearer <token>} */
        private String token;

        public AuthResult(Long userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname;
        }

        public Long getUserId() {
            return userId;
        }

        public String getNickname() {
            return nickname;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
