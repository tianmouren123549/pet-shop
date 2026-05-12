package com.gzu.petshop.dto.user;

/**
 * 用户资料（前端个人中心）；{@code avatarUrl} 对应表 {@code user.avatar_url}。
 */
public class UserProfileDTO {
    private Long userId;
    private String nickname;
    private String phone;
    private String email;
    private String avatarUrl;
    /** 用户首页宠物偏好：cat/dog/both。 */
    private String petPreference;
    /** NORMAL / PLUS */
    private String membershipTier;
    /** ISO 日期时间或 null */
    private String plusExpiresAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPetPreference() {
        return petPreference;
    }

    public void setPetPreference(String petPreference) {
        this.petPreference = petPreference;
    }

    public String getMembershipTier() {
        return membershipTier;
    }

    public void setMembershipTier(String membershipTier) {
        this.membershipTier = membershipTier;
    }

    public String getPlusExpiresAt() {
        return plusExpiresAt;
    }

    public void setPlusExpiresAt(String plusExpiresAt) {
        this.plusExpiresAt = plusExpiresAt;
    }
}
