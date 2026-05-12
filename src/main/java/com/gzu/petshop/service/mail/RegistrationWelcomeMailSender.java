package com.gzu.petshop.service.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 注册成功后向用户邮箱发送<strong>欢迎通知</strong>（非验证码链路）。
 * 需配置 {@code spring.mail.*} 且 {@code app.mail.registration-welcome-enabled=true}。
 */
@Service
public class RegistrationWelcomeMailSender {

    private static final Logger log = LoggerFactory.getLogger(RegistrationWelcomeMailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    @Value("${app.mail.registration-welcome-enabled:false}")
    private boolean welcomeEnabled;

    /** 发件人；为空则使用 {@code spring.mail.username} */
    @Value("${app.mail.from:}")
    private String mailFrom;

    public RegistrationWelcomeMailSender(ObjectProvider<JavaMailSender> mailSenderProvider, Environment environment) {
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
    }

    /**
     * 发送用户注册欢迎通知。
     */
    public void sendUserRegistrationWelcome(String toEmail, String nickname) {
        if (!welcomeEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[mail] 已开启注册欢迎通知，但未配置 spring.mail.host，跳过发送 to={}", toEmail);
            return;
        }
        String from = resolveFromAddress();
        if (from.isBlank()) {
            log.warn("[mail] 未配置 app.mail.from 或 spring.mail.username，跳过发送 to={}", toEmail);
            return;
        }
        String safeNick = nickname == null || nickname.isBlank() ? "用户" : escapeHtml(nickname.trim());
        String subject = "【宠物商城】注册欢迎通知";
        String html =
                "<div style=\"font-family:Microsoft YaHei,PingFang SC,sans-serif;line-height:1.6;color:#1f2937;\">"
                        + "<p>" + safeNick + "，您好！</p>"
                        + "<p>您已在<strong>宠物商城</strong>完成注册，感谢您的加入。</p>"
                        + "<p>这是一封系统自动发送的<strong>欢迎通知</strong>，请勿直接回复本邮件。</p>"
                        + "<p style=\"color:#64748b;font-size:13px;margin-top:24px;\">祝您购物愉快！</p>"
                        + "</div>";
        sendHtml(sender, from, toEmail.trim(), subject, html);
    }

    /**
     * 商家注册且填写了联系邮箱时发送欢迎通知。
     */
    public void sendMerchantRegistrationWelcome(String toEmail, String shopName, String username) {
        if (!welcomeEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[mail] 已开启注册欢迎通知，但未配置 spring.mail.host，跳过发送 to={}", toEmail);
            return;
        }
        String from = resolveFromAddress();
        if (from.isBlank()) {
            log.warn("[mail] 未配置 app.mail.from 或 spring.mail.username，跳过发送 to={}", toEmail);
            return;
        }
        String shop = shopName == null || shopName.isBlank() ? "您的店铺" : escapeHtml(shopName.trim());
        String user = username == null || username.isBlank() ? "" : escapeHtml(username.trim());
        String subject = "【宠物商城】商家账号注册欢迎通知";
        String html =
                "<div style=\"font-family:Microsoft YaHei,PingFang SC,sans-serif;line-height:1.6;color:#1f2937;\">"
                        + "<p>您好！</p>"
                        + "<p>店铺「<strong>" + shop + "</strong>」已完成商家端注册"
                        + (user.isEmpty() ? "" : "（登录账号：" + user + "）")
                        + "。</p>"
                        + "<p>这是一封系统自动发送的<strong>欢迎通知</strong>，请勿直接回复本邮件。</p>"
                        + "<p style=\"color:#64748b;font-size:13px;margin-top:24px;\">祝商祺！</p>"
                        + "</div>";
        sendHtml(sender, from, toEmail.trim(), subject, html);
    }

    private String resolveFromAddress() {
        if (mailFrom != null && !mailFrom.isBlank()) {
            return mailFrom.trim();
        }
        String u = environment.getProperty("spring.mail.username", "");
        return u == null ? "" : u.trim();
    }

    private void sendHtml(JavaMailSender sender, String from, String to, String subject, String htmlBody) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("[mail] 注册欢迎通知已发送 to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("[mail] 注册欢迎通知发送失败 to={} subject={}", to, subject, e);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
