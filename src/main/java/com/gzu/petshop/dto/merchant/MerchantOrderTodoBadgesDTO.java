package com.gzu.petshop.dto.merchant;

/**
 * 商家订单管理顶栏/筛选：是否存在待发货（{@code PAID}）订单（不返回条数；待支付不做红点提示）。
 *
 * @param pendingShipment 存在已支付待发货订单
 */
public record MerchantOrderTodoBadgesDTO(boolean pendingShipment) {
}
