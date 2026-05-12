-- 从业务库导出订单正样本：user_id + 已购 product_id
-- 在 MySQL 客户端执行后，用「导出结果」或以下方式落盘为 CSV（UTF-8，表头一行）

-- 1) 用户-商品正样本（示例：已支付/已完成订单）
SELECT DISTINCT
  o.user_id   AS user_id,
  oi.product_id AS product_id
FROM order_item oi
JOIN orders o ON o.order_id = oi.order_id
WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
ORDER BY o.user_id, oi.product_id;

-- 2) 商品静态特征（可选，用于增强 XGBoost 特征）
SELECT
  p.product_id,
  p.category_id,
  p.brand_id,
  p.price,
  p.stock
FROM product p
WHERE p.status = 1;
