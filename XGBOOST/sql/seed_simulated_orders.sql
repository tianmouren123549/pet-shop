-- =============================================================================
-- 模拟订单数据：仅通过 INSERT 写入 orders / order_item（不依赖 CSV 导出）
-- 执行前请确认：库中已有 user、product，且 product.status=1、stock>0 的商品足够
-- 可在 MySQL 客户端 / Navicat / DBeaver 中整段执行
-- =============================================================================

SET @sim_prefix := 'SIM20260419';

-- 若不想重复执行产生 order_no 冲突，可先删本脚本生成的订单（按订单号前缀）
-- DELETE oi FROM order_item oi INNER JOIN orders o ON o.order_id = oi.order_id WHERE o.order_no LIKE CONCAT(@sim_prefix, '%');
-- DELETE FROM orders WHERE order_no LIKE CONCAT(@sim_prefix, '%');

DELIMITER $$

DROP PROCEDURE IF EXISTS petshop_seed_simulated_orders$$

CREATE PROCEDURE petshop_seed_simulated_orders(
  IN p_num_orders INT,
  IN p_status VARCHAR(16)
)
BEGIN
  DECLARE v_i INT DEFAULT 0;
  DECLARE v_uid BIGINT;
  DECLARE v_pid BIGINT;
  DECLARE v_mid BIGINT;
  DECLARE v_price DECIMAL(10,2);
  DECLARE v_user_cnt INT;
  DECLARE v_prod_cnt INT;
  DECLARE v_onum VARCHAR(64);

  SELECT COUNT(*) INTO v_user_cnt FROM `user` WHERE `status` = 1;
  SELECT COUNT(*) INTO v_prod_cnt FROM `product` WHERE `status` = 1 AND `stock` > 0;

  IF v_user_cnt < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user 表无可用用户(status=1)，请先注册至少一个用户';
  END IF;

  IF v_prod_cnt < 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'product 表无上架且有库存商品，请先维护商品';
  END IF;

  WHILE v_i < p_num_orders DO
    SET v_i = v_i + 1;

    SET v_uid = (
      SELECT u.user_id FROM `user` u
      WHERE u.status = 1
      ORDER BY u.user_id
      LIMIT 1 OFFSET MOD(v_i - 1, v_user_cnt)
    );

    SET v_pid = (
      SELECT p.product_id FROM `product` p
      WHERE p.status = 1 AND p.stock > 0
      ORDER BY p.product_id
      LIMIT 1 OFFSET MOD(v_i - 1, v_prod_cnt)
    );

    SET v_mid = (SELECT merchant_id FROM `product` WHERE product_id = v_pid LIMIT 1);
    SET v_price = (SELECT price FROM `product` WHERE product_id = v_pid LIMIT 1);

    SET v_onum = CONCAT(@sim_prefix, LPAD(v_i, 5, '0'));

    INSERT INTO `orders` (`order_no`, `user_id`, `total_amount`, `pay_amount`, `status`, `paid_at`, `created_at`)
    VALUES (v_onum, v_uid, v_price, v_price, p_status, NOW(), NOW());

    INSERT INTO `order_item` (`order_id`, `product_id`, `merchant_id`, `quantity`, `item_price`)
    VALUES (LAST_INSERT_ID(), v_pid, v_mid, 1, v_price);
  END WHILE;
END$$

DELIMITER ;

-- 生成 60 条已完成订单（可按需改数字；状态也可用 PAID）
CALL petshop_seed_simulated_orders(60, 'COMPLETED');

DROP PROCEDURE IF EXISTS petshop_seed_simulated_orders;

-- 校验：应能看到 user_id + product_id
-- SELECT o.user_id, oi.product_id, o.status, o.order_no
-- FROM orders o
-- JOIN order_item oi ON oi.order_id = o.order_id
-- WHERE o.order_no LIKE CONCAT(@sim_prefix, '%')
-- LIMIT 10;
