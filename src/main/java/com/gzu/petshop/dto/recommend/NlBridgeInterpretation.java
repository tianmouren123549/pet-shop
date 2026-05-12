package com.gzu.petshop.dto.recommend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然语言 → 推荐侧可消费的语义槽位（由豆包输出 JSON，失败时由规则兜底）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NlBridgeInterpretation {

    /**
     * dog | cat | unknown
     */
    private String petSpecies;

    /**
     * staple_food | snack | supply | unknown
     */
    private String productIntent;

    /**
     * puppy_kitten | adult | senior | unknown
     */
    private String lifeStage;

    private List<String> titleKeywords = new ArrayList<>();

    /** 供接口与运营侧展示的「模型可读查询摘要」，如「犬用成犬主粮·中大型犬」 */
    private String modelQuerySummary;

    /** 对用户展示的简短说明（可与下方推荐列表一起展示） */
    private String assistantMessage;

    public String getPetSpecies() {
        return petSpecies;
    }

    public void setPetSpecies(String petSpecies) {
        this.petSpecies = petSpecies;
    }

    public String getProductIntent() {
        return productIntent;
    }

    public void setProductIntent(String productIntent) {
        this.productIntent = productIntent;
    }

    public String getLifeStage() {
        return lifeStage;
    }

    public void setLifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
    }

    public List<String> getTitleKeywords() {
        return titleKeywords;
    }

    public void setTitleKeywords(List<String> titleKeywords) {
        this.titleKeywords = titleKeywords != null ? titleKeywords : new ArrayList<>();
    }

    public String getModelQuerySummary() {
        return modelQuerySummary;
    }

    public void setModelQuerySummary(String modelQuerySummary) {
        this.modelQuerySummary = modelQuerySummary;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }
}
