package com.gzu.petshop.service.mail;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步发送注册欢迎通知，避免 SMTP 阻塞注册接口。
 */
@Service
public class RegistrationWelcomeMailNotifier {

    private final RegistrationWelcomeMailSender mailSender;

    public RegistrationWelcomeMailNotifier(RegistrationWelcomeMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void notifyUserRegistered(String email, String nickname) {
        mailSender.sendUserRegistrationWelcome(email, nickname);
    }

    @Async
    public void notifyMerchantRegistered(String email, String shopName, String username) {
        mailSender.sendMerchantRegistrationWelcome(email, shopName, username);
    }
}
