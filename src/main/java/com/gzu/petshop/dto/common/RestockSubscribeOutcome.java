package com.gzu.petshop.dto.common;

/**
 * 用户订阅到货提醒的结果：{@link #errorMessage()} 非空表示失败；否则 {@link #successMessage()} 为弹层/Toast 提示文案。
 */
public record RestockSubscribeOutcome(String errorMessage, String successMessage) {

    /**
     * @param message 错误原因（用户可读中文）
     */
    public static RestockSubscribeOutcome error(String message) {
        return new RestockSubscribeOutcome(message, null);
    }

    /**
     * @param message 成功提示（用户可读中文，符合电商场景）
     */
    public static RestockSubscribeOutcome ok(String message) {
        return new RestockSubscribeOutcome(null, message);
    }
}
