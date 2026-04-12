package com.gzu.petshop.dto.common;

import java.util.List;

/**
 * 用户「联系商家」列表：哪些店铺存在未读的商家回复（仅 id 列表，前端用红点标识，不展示条数）。
 */
public record ChatUnreadMerchantsDTO(List<Long> merchantIds) {
}
