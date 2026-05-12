package com.gzu.petshop.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 火山方舟 OpenAI 兼容接口 {@code /chat/completions} 响应（仅解析本业务用到的字段）。
 * {@code message.content} 可能为字符串或多段文本数组，统一解析为文本。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcengineChatCompletionResponse {

    private List<Choice> choices;

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private JsonNode content;

        @JsonProperty("content")
        public void setContent(JsonNode content) {
            this.content = content;
        }

        public JsonNode getContent() {
            return content;
        }

        /**
         * 豆包/方舟部分版本返回 {@code content} 为 JSON 数组（多段文本），此处合并为单字符串。
         */
        public String resolveTextContent() {
            if (content == null || content.isNull()) {
                return null;
            }
            if (content.isTextual()) {
                return content.asText();
            }
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode n : content) {
                    if (n == null || n.isNull()) {
                        continue;
                    }
                    if (n.isTextual()) {
                        sb.append(n.asText());
                    } else if (n.has("text")) {
                        sb.append(n.get("text").asText(""));
                    } else if (n.has("content")) {
                        JsonNode c = n.get("content");
                        if (c != null && c.isTextual()) {
                            sb.append(c.asText());
                        }
                    }
                }
                String s = sb.toString().trim();
                return s.isEmpty() ? null : s;
            }
            return content.asText();
        }
    }
}
