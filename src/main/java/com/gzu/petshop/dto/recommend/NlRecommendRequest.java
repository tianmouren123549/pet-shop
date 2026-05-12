package com.gzu.petshop.dto.recommend;

public class NlRecommendRequest {

    /** 用户自然语言，如「我的金毛吃什么」 */
    private String question;

    /** 返回条数，默认 5，最大 5（与接口约定一致） */
    private Integer topN;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getTopN() {
        return topN;
    }

    public void setTopN(Integer topN) {
        this.topN = topN;
    }
}
