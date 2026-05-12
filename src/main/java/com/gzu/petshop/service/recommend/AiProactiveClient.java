package com.gzu.petshop.service.recommend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.ai.VolcengineChatCompletionResponse;
import com.gzu.petshop.dto.recommend.NlBridgeInterpretation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 主动追问 LLM：支持
 * <ul>
 *   <li>火山引擎方舟（豆包）OpenAI 兼容 {@code POST /chat/completions}</li>
 *   <li>自建编排 {@code POST {baseUrl}/v1/proactive/turn}</li>
 * </ul>
 */
@Service
public class AiProactiveClient {

    private static final Logger log = LoggerFactory.getLogger(AiProactiveClient.class);

    private static final String PROVIDER_VOLCENGINE = "volcengine";
    private static final String PROVIDER_CUSTOM = "custom";

    /** 方舟 {@code /responses}（与控制台 REST 多模态示例一致）；{@code chat_completions} 为旧版 OpenAI 兼容对话。 */
    private static final String VOLC_STYLE_RESPONSES = "responses";
    private static final String VOLC_STYLE_CHAT = "chat_completions";

    private static final String SYSTEM_PROMPT = """
            你是宠物电商导购助手。结合用户画像、推荐候选商品、常购记录与对话历史，用中文协助选购；语气自然亲切，像真人店员，回复简洁有条理。
            若用户寒暄、打招呼、致谢、闲聊或表达情绪（例如「你好」「在吗」「谢谢」「算了」），应先正面回应这句话本身，再继续或过渡到与宠物用品相关的话题；不要无视用户当前输入、重复同一套固定开场白。
            需要缩小范围时，可追问 1～2 个具体问题；不要编造不存在的商品。
            商品展示规则：在用户尚未明确要买什么、或你仍在追问时，highlightProductIds 必须为空数组 []，此时前端不展示任何商品卡片。仅当你已根据对话给出**具体商品推荐**（用户意图与候选中的 title、score 已对齐）时，才把对应 productId 填入 highlightProductIds：能非常明确锁一款则只填 1 个，否则 2～5 个，不要超过 5 个。id 必须来自 recommendationCandidates，不得编造。
            必须只输出一行合法 JSON，不要 markdown 代码块，格式严格为：
            {"assistantMessage":"给用户的回复正文","highlightProductIds":[可选的商品id数字数组，没有则[]],"done":false}
            done 始终填 false（对话可继续，系统不据此结束会话）。""";

    /**
     * 自然语言 → 结构化槽位：供站内 XGB/离线推荐结果做类目与标题过滤，不作为模型特征向量输入。
     */
    private static final String NL_BRIDGE_SYSTEM_PROMPT = """
            你是宠物电商检索助手。用户会用口语提问（如「我的金毛吃什么」「幼猫软便吃什么粮」）。
            请抽取：犬/猫、主粮或零食/用品、生命周期（幼/成/老）、可用于匹配商品标题的中文关键词（含品种、粮型如成犬粮等）。
            重要：petSpecies 为 cat 时，titleKeywords 中不得出现「幼犬」「犬粮」「狗粮」「成犬」等仅针对犬的词；为 dog 时不得出现「幼猫」「猫粮」「猫条」等仅针对猫的词。
            若用户意图非常具体且候选中有明显最佳项，assistantMessage 可明确主推一款；最终返回给前端的商品条数由系统截断为至多 5 条（可能仅 1 条）。
            说明：站内推荐分已由 XGBoost 等模型离线或在线算好；你只负责把问题「翻译」成可筛选的槽位与一两句用户可见说明，不要编造具体商品名。
            必须只输出一行合法 JSON，不要 markdown 代码块，格式严格为：
            {"petSpecies":"dog或cat或unknown","productIntent":"staple_food或snack或supply或unknown","lifeStage":"puppy_kitten或adult或senior或unknown","titleKeywords":["关键词1","关键词2"],"modelQuerySummary":"供系统检索与日志用的简短查询描述（中文）","assistantMessage":"给用户看的友好回复（中文）"}""";

    private final RestTemplate restTemplate;
    private final RestTemplate volcRestTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String provider;
    private final String customBaseUrl;
    private final String volcBaseUrl;
    private final String volcApiKey;
    private final String volcEndpointId;
    private final String volcApiStyle;

    public AiProactiveClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.proactive-enabled:false}") boolean enabled,
            @Value("${app.ai.provider:volcengine}") String provider,
            @Value("${app.ai.proactive-base-url:}") String customBaseUrl,
            @Value("${app.ai.volcengine.base-url:https://ark.cn-beijing.volces.com/api/v3}") String volcBaseUrl,
            @Value("${app.ai.volcengine.api-key:}") String volcApiKeyEnv,
            @Value("${app.ai.volcengine.api-key-inline:}") String volcApiKeyInline,
            @Value("${app.ai.volcengine.endpoint-id:}") String volcEndpointId,
            @Value("${app.ai.volcengine.api-style:responses}") String volcApiStyle
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        String p = provider != null ? provider.trim().toLowerCase() : PROVIDER_VOLCENGINE;
        if ("doubao".equals(p) || "ark".equals(p)) {
            p = PROVIDER_VOLCENGINE;
        }
        this.provider = p;
        String c = customBaseUrl != null ? customBaseUrl.trim() : "";
        this.customBaseUrl = c.endsWith("/") ? c.substring(0, c.length() - 1) : c;
        String v = volcBaseUrl != null ? volcBaseUrl.trim() : "";
        this.volcBaseUrl = v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
        this.volcApiKey = resolveVolcApiKey(volcApiKeyEnv, volcApiKeyInline);
        this.volcEndpointId = volcEndpointId != null ? volcEndpointId.trim() : "";
        String vs = volcApiStyle != null ? volcApiStyle.trim().toLowerCase() : VOLC_STYLE_RESPONSES;
        if ("chat".equals(vs) || "openai".equals(vs)) {
            vs = VOLC_STYLE_CHAT;
        }
        this.volcApiStyle = vs.equals(VOLC_STYLE_CHAT) ? VOLC_STYLE_CHAT : VOLC_STYLE_RESPONSES;

        SimpleClientHttpRequestFactory shortTimeout = new SimpleClientHttpRequestFactory();
        shortTimeout.setConnectTimeout(3_000);
        shortTimeout.setReadTimeout(25_000);

        SimpleClientHttpRequestFactory volcTimeout = new SimpleClientHttpRequestFactory();
        volcTimeout.setConnectTimeout(5_000);
        volcTimeout.setReadTimeout(60_000);

        if (!enabled) {
            this.restTemplate = null;
            this.volcRestTemplate = null;
        } else if (PROVIDER_CUSTOM.equals(this.provider)) {
            this.volcRestTemplate = null;
            this.restTemplate = this.customBaseUrl.isEmpty() ? null : new RestTemplate(shortTimeout);
        } else if (PROVIDER_VOLCENGINE.equals(this.provider) && volcengineReady()) {
            this.restTemplate = null;
            this.volcRestTemplate = new RestTemplate(volcTimeout);
        } else {
            this.restTemplate = null;
            this.volcRestTemplate = null;
        }
    }

    @PostConstruct
    void logAiProactiveStartup() {
        if (!enabled) {
            log.info("[ai-proactive] 已关闭（app.ai.proactive-enabled=false），仅使用内置话术");
            return;
        }
        if (!PROVIDER_VOLCENGINE.equals(provider)) {
            return;
        }
        if (volcengineReady()) {
            String path = VOLC_STYLE_CHAT.equals(volcApiStyle) ? "/chat/completions" : "/responses";
            log.info(
                    "[ai-proactive] 火山方舟豆包已就绪：POST {}{}，model={}，api-style={}",
                    volcBaseUrl,
                    path,
                    volcEndpointId,
                    volcApiStyle);
            // 控制台「API Key」列与推理接入点 ID 均以 ark-/ep- 开头均属正常，仅以「两字段字符串完全相同」作误配提示。
            if (!volcEndpointId.isEmpty() && volcApiKey.equals(volcEndpointId)) {
                log.warn(
                        "[ai-proactive] API Key 与 endpoint-id 字符串完全相同；若调用失败，请核对控制台「API Key」列与推理接入点「model」是否复制成了两份相同内容。");
            }
        } else {
            logVolcengineNotReadyDiagnostics();
        }
    }

    /**
     * 诊断「未就绪」原因：只打布尔与长度，不打密钥。文档里的 {@code export ARK_API_KEY} 仅在当前 shell 生效；
     * 若从 IDE/Windows「开始菜单」启动 Java，通常<strong>不会</strong>继承你在终端里临时 export 的变量，须在用户级环境变量或 IDE Run Configuration 里配置。
     */
    private void logVolcengineNotReadyDiagnostics() {
        boolean keyResolved = volcApiKey != null && !volcApiKey.isEmpty();
        boolean envVolc = notBlank(System.getenv("VOLCENGINE_API_KEY"));
        boolean envArk = notBlank(System.getenv("ARK_API_KEY"));
        boolean envDoubao = notBlank(System.getenv("DOUBAO_API_KEY"));
        log.warn(
                "[ai-proactive] 火山方舟未就绪 — 诊断（不含密钥）：合并后的 api-key 是否非空={}（false 表示密钥未进入 JVM）；"
                        + "进程环境变量是否设置 VOLCENGINE_API_KEY={} ARK_API_KEY={} DOUBAO_API_KEY={}；"
                        + "endpoint-id 非空={}；base-url 非空={}",
                keyResolved,
                envVolc,
                envArk,
                envDoubao,
                !volcEndpointId.isEmpty(),
                !volcBaseUrl.isEmpty());
        log.warn(
                "[ai-proactive] 处理建议：① Windows 用户：系统设置 → 环境变量 → 用户变量 新增 ARK_API_KEY，或 IDE「运行/调试配置」→ Environment 添加 ARK_API_KEY（与文档一致），然后<strong>完全重启</strong>后端进程；"
                        + "② 或在 application-local.properties 写 app.ai.volcengine.api-key-inline=（勿提交 Git）；"
                        + "③ 勿仅用文档里的 bash export：除非在同一终端用 java -jar 启动，否则子进程读不到。");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 顺序：① Spring 注入的 api-key（已展开环境变量占位符）② api-key-inline ③ 进程环境变量直读（避免 IDE 未把变量注入 Spring 时仍为空的兜底）。
     */
    private static String resolveVolcApiKey(String fromSpring, String inline) {
        String k1 = normalizeApiKey(fromSpring);
        if (!k1.isEmpty()) {
            return k1;
        }
        String k2 = normalizeApiKey(inline);
        if (!k2.isEmpty()) {
            return k2;
        }
        for (String name : List.of("VOLCENGINE_API_KEY", "ARK_API_KEY", "DOUBAO_API_KEY")) {
            String v = normalizeApiKey(System.getenv(name));
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String normalizeApiKey(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.regionMatches(true, 0, "bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    private boolean volcengineReady() {
        return PROVIDER_VOLCENGINE.equals(provider) && !volcApiKey.isEmpty() && !volcEndpointId.isEmpty() && !volcBaseUrl.isEmpty();
    }

    /**
     * 调用方舟，将用户自然语言解析为 {@link NlBridgeInterpretation}（供推荐列表过滤）。
     * 未启用 AI 或未就绪时返回 empty。
     */
    public Optional<NlBridgeInterpretation> interpretNlForRecommendation(String userQuestion) {
        if (!enabled || volcRestTemplate == null || userQuestion == null || userQuestion.isBlank()) {
            return Optional.empty();
        }
        if (!PROVIDER_VOLCENGINE.equals(provider) || !volcengineReady()) {
            return Optional.empty();
        }
        String q = userQuestion.trim();
        if (VOLC_STYLE_RESPONSES.equals(volcApiStyle)) {
            String combined = NL_BRIDGE_SYSTEM_PROMPT + "\n\n用户问题：\n" + q;
            return volcengineResponsesAssistantRaw(combined).flatMap(this::parseNlBridgeJson);
        }
        return volcengineChatCompletionsAssistantRaw(NL_BRIDGE_SYSTEM_PROMPT, "用户问题：\n" + q)
                .flatMap(this::parseNlBridgeJson);
    }

    /**
     * @param body 用户画像、候选商品摘要、对话历史等（由 {@link #buildPayload} 组装）
     */
    public AiProactiveTurnOutcome turnWithOutcome(Map<String, Object> body) {
        if (!enabled || body == null || body.isEmpty()) {
            return new AiProactiveTurnOutcome(Optional.empty(), ProactiveLlmKind.TEMPLATE);
        }
        if (volcRestTemplate != null) {
            Optional<ProactiveAiHttpBody> o = turnVolcengine(body);
            return new AiProactiveTurnOutcome(
                    o,
                    o.isPresent() ? ProactiveLlmKind.DOUBAO : ProactiveLlmKind.TEMPLATE);
        }
        if (restTemplate != null) {
            Optional<ProactiveAiHttpBody> o = turnCustom(body);
            return new AiProactiveTurnOutcome(
                    o,
                    o.isPresent() ? ProactiveLlmKind.CUSTOM : ProactiveLlmKind.TEMPLATE);
        }
        if (enabled && PROVIDER_VOLCENGINE.equals(provider) && !volcengineReady()) {
            log.warn(
                    "[ai-proactive] 火山引擎未就绪：进程内未读到 API Key。请任选：① 系统环境变量 VOLCENGINE_API_KEY / ARK_API_KEY / DOUBAO_API_KEY（需重启后端）；"
                            + "② application-local.properties 中 app.ai.volcengine.api-key-inline=密钥；③ IDE 运行配置里添加上述环境变量。并确认 app.ai.volcengine.endpoint-id 非空。");
        }
        if (enabled && PROVIDER_CUSTOM.equals(provider) && restTemplate == null) {
            log.warn("[ai-proactive] 自建编排未配置：请设置 app.ai.proactive-base-url");
        }
        return new AiProactiveTurnOutcome(Optional.empty(), ProactiveLlmKind.TEMPLATE);
    }

    private Optional<ProactiveAiHttpBody> turnVolcengine(Map<String, Object> body) {
        if (VOLC_STYLE_RESPONSES.equals(volcApiStyle)) {
            return turnVolcengineResponses(body);
        }
        return turnVolcengineChatCompletions(body);
    }

    /**
     * 方舟 Responses API：<a href="https://ark.cn-beijing.volces.com/api/v3/responses">/api/v3/responses</a>，
     * 与控制台「REST API」多模态示例同一协议（本处仅用 {@code input_text}）。
     */
    private Optional<ProactiveAiHttpBody> turnVolcengineResponses(Map<String, Object> body) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("[ai-proactive] 上下文序列化失败: {}", e.getMessage());
            return Optional.empty();
        }
        String userContent = "以下为当前请求的上下文 JSON，请据此生成回复：\n" + contextJson;
        String combined = SYSTEM_PROMPT + "\n\n---\n\n" + userContent;
        return volcengineResponsesAssistantRaw(combined).flatMap(this::parseModelJson);
    }

    /**
     * Responses API：单条 user 文本 → 助手原始文本（不含 JSON 解析）。
     */
    private Optional<String> volcengineResponsesAssistantRaw(String combinedUserText) {
        String url = volcBaseUrl + "/responses";
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> userTurn = new LinkedHashMap<>();
        userTurn.put("role", "user");
        userTurn.put("content", List.of(Map.of("type", "input_text", "text", combinedUserText)));
        input.add(userTurn);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", volcEndpointId);
        req.put("input", input);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(volcApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);
            ResponseEntity<String> resp = volcRestTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String raw = extractResponsesApiAssistantText(root);
            if (raw == null || raw.isBlank()) {
                log.warn(
                        "[ai-proactive] Responses API 未解析到助手文本。响应片段: {}",
                        preview(resp.getBody()));
                return Optional.empty();
            }
            return Optional.of(raw);
        } catch (HttpStatusCodeException e) {
            logVolcHttpError(e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("[ai-proactive] Responses API 响应非 JSON: {}", e.getMessage());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("[ai-proactive] 火山引擎 Responses 调用失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProactiveAiHttpBody> turnVolcengineChatCompletions(Map<String, Object> body) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("[ai-proactive] 上下文序列化失败: {}", e.getMessage());
            return Optional.empty();
        }
        String userContent = "以下为当前请求的上下文 JSON，请据此生成回复：\n" + contextJson;
        return volcengineChatCompletionsAssistantRaw(SYSTEM_PROMPT, userContent).flatMap(this::parseModelJson);
    }

    private Optional<String> volcengineChatCompletionsAssistantRaw(String systemPrompt, String userContent) {
        String url = volcBaseUrl + "/chat/completions";
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent));

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", volcEndpointId);
        req.put("messages", messages);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(volcApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);
            ResponseEntity<VolcengineChatCompletionResponse> resp =
                    volcRestTemplate.postForEntity(url, entity, VolcengineChatCompletionResponse.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            VolcengineChatCompletionResponse r = resp.getBody();
            if (r.getChoices() == null || r.getChoices().isEmpty() || r.getChoices().get(0).getMessage() == null) {
                return Optional.empty();
            }
            String raw = r.getChoices().get(0).getMessage().resolveTextContent();
            return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw);
        } catch (HttpStatusCodeException e) {
            logVolcHttpError(e);
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("[ai-proactive] 火山引擎 chat/completions 调用失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<NlBridgeInterpretation> parseNlBridgeJson(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }
        String json = stripMarkdownFence(rawContent.trim());
        Optional<NlBridgeInterpretation> first = tryParseNlBridge(json);
        if (first.isPresent()) {
            return first;
        }
        int a = json.indexOf('{');
        int b = json.lastIndexOf('}');
        if (a >= 0 && b > a) {
            Optional<NlBridgeInterpretation> sub = tryParseNlBridge(json.substring(a, b + 1));
            if (sub.isPresent()) {
                return sub;
            }
        }
        log.warn("[ai-proactive] NL 桥接 JSON 解析失败，将使用规则兜底。片段: {}", preview(rawContent));
        return Optional.empty();
    }

    private Optional<NlBridgeInterpretation> tryParseNlBridge(String json) {
        try {
            NlBridgeInterpretation out = objectMapper.readValue(json, NlBridgeInterpretation.class);
            if (out.getAssistantMessage() == null || out.getAssistantMessage().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(out);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private void logVolcHttpError(HttpStatusCodeException e) {
        String errBody = e.getResponseBodyAsString();
        String snippet = errBody == null ? "" : errBody.length() > 800 ? errBody.substring(0, 800) + "…" : errBody;
        log.warn(
                "[ai-proactive] 火山引擎 HTTP {}：{} — 将使用内置话术。请核对 Key、接入点、api-style（responses/chat_completions）。body: {}",
                e.getStatusCode().value(),
                e.getStatusText(),
                snippet);
    }

    /**
     * 从 Responses API 返回体中提取助手文本（兼容 output 列表、content 数组、output_text 等常见形态）。
     */
    private static String extractResponsesApiAssistantText(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode block : output) {
                if (block == null || block.isNull()) {
                    continue;
                }
                String fromBlock = extractTextFromContentNode(block.get("content"));
                if (fromBlock != null && !fromBlock.isBlank()) {
                    return fromBlock;
                }
            }
        }
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode msg = choices.get(0).get("message");
            if (msg != null) {
                JsonNode c = msg.get("content");
                if (c != null && c.isTextual()) {
                    return c.asText();
                }
            }
        }
        return null;
    }

    private static String extractTextFromContentNode(JsonNode content) {
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.has("text")) {
                    sb.append(part.get("text").asText(""));
                } else if ("output_text".equals(part.path("type").asText()) && part.has("text")) {
                    sb.append(part.get("text").asText(""));
                } else if ("input_text".equals(part.path("type").asText()) && part.has("text")) {
                    sb.append(part.get("text").asText(""));
                }
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private Optional<ProactiveAiHttpBody> turnCustom(Map<String, Object> body) {
        String url = customBaseUrl + "/v1/proactive/turn";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<ProactiveAiHttpBody> resp =
                    restTemplate.postForEntity(url, entity, ProactiveAiHttpBody.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            ProactiveAiHttpBody r = resp.getBody();
            if (r.getAssistantMessage() == null || r.getAssistantMessage().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(r);
        } catch (RestClientException e) {
            log.warn("[ai-proactive] 自建编排调用失败，将使用内置话术: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProactiveAiHttpBody> parseModelJson(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }
        String json = stripMarkdownFence(rawContent.trim());
        Optional<ProactiveAiHttpBody> first = tryParseProactiveBody(json);
        if (first.isPresent()) {
            return first;
        }
        int a = json.indexOf('{');
        int b = json.lastIndexOf('}');
        if (a >= 0 && b > a) {
            Optional<ProactiveAiHttpBody> sub = tryParseProactiveBody(json.substring(a, b + 1));
            if (sub.isPresent()) {
                return sub;
            }
        }
        log.warn("[ai-proactive] 模型返回非预期 JSON（无法解析 assistantMessage），将使用内置话术。原文片段: {}", preview(rawContent));
        return Optional.empty();
    }

    private Optional<ProactiveAiHttpBody> tryParseProactiveBody(String json) {
        try {
            ProactiveAiHttpBody out = objectMapper.readValue(json, ProactiveAiHttpBody.class);
            if (out.getAssistantMessage() == null || out.getAssistantMessage().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(out);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 240 ? t.substring(0, 240) + "…" : t;
    }

    private static String stripMarkdownFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end >= 0) {
                t = t.substring(0, end).trim();
            }
        }
        return t;
    }

    /**
     * 外部服务 / 模型解析后的统一结构。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class ProactiveAiHttpBody {
        private String assistantMessage;
        private List<Long> highlightProductIds;
        private Boolean done;

        public String getAssistantMessage() {
            return assistantMessage;
        }

        public void setAssistantMessage(String assistantMessage) {
            this.assistantMessage = assistantMessage;
        }

        public List<Long> getHighlightProductIds() {
            return highlightProductIds;
        }

        public void setHighlightProductIds(List<Long> highlightProductIds) {
            this.highlightProductIds = highlightProductIds;
        }

        public Boolean getDone() {
            return done;
        }

        public void setDone(Boolean done) {
            this.done = done;
        }
    }

    /** 组装发给 LLM / 编排服务的 body（扁平 + history 嵌套）。 */
    public static Map<String, Object> buildPayload(
            long userId,
            Long sessionId,
            String petPreference,
            String membershipTier,
            List<Map<String, Object>> recommendationSummaries,
            List<Map<String, Object>> frequentSummaries,
            List<Map<String, String>> chatHistory,
            String latestUserMessage
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        if (sessionId != null) {
            m.put("sessionId", sessionId);
        }
        m.put("petPreference", petPreference);
        m.put("membershipTier", membershipTier);
        m.put("recommendationCandidates", recommendationSummaries);
        m.put("frequentProducts", frequentSummaries);
        m.put("history", chatHistory);
        m.put("userMessage", latestUserMessage != null ? latestUserMessage : "");
        return m;
    }

    public static List<Map<String, Object>> summarizeRecommendations(
            List<com.gzu.petshop.dto.user.RecommendationItemDTO> items
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (com.gzu.petshop.dto.user.RecommendationItemDTO it : items) {
            if (it == null || it.getProductId() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", it.getProductId());
            if (it.getProduct() != null) {
                row.put("title", it.getProduct().getTitle());
            }
            row.put("score", it.getScore());
            out.add(row);
        }
        return out;
    }

    public static List<Map<String, Object>> summarizeFrequent(
            List<com.gzu.petshop.dto.user.FrequentProductItemDTO> items
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (com.gzu.petshop.dto.user.FrequentProductItemDTO it : items) {
            if (it == null || it.getProductId() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", it.getProductId());
            row.put("title", it.getTitle());
            row.put("buyCount", it.getBuyCount());
            out.add(row);
        }
        return out;
    }
}
