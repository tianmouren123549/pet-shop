package com.gzu.petshop.service.recommend;

/**
 * 主动推荐一轮回复所用 LLM 来源（供前端展示接入状态）。
 */
public enum ProactiveLlmKind {
    /** 未调用大模型或调用失败，使用内置模板 */
    TEMPLATE,
    /** 火山引擎方舟 · 豆包 */
    DOUBAO,
    /** 自建编排 HTTP */
    CUSTOM
}
