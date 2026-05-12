package com.gzu.petshop.service.recommend;

import com.gzu.petshop.service.recommend.AiProactiveClient.ProactiveAiHttpBody;

import java.util.Optional;

public final class AiProactiveTurnOutcome {

    private final Optional<ProactiveAiHttpBody> body;
    private final ProactiveLlmKind kind;

    public AiProactiveTurnOutcome(Optional<ProactiveAiHttpBody> body, ProactiveLlmKind kind) {
        this.body = body != null ? body : Optional.empty();
        this.kind = kind != null ? kind : ProactiveLlmKind.TEMPLATE;
    }

    public Optional<ProactiveAiHttpBody> getBody() {
        return body;
    }

    public ProactiveLlmKind getKind() {
        return kind;
    }
}
