# 数据库表结构变更说明

## 1. 用户行为返利流水订单表 (`user_behavior_rebate_order`)

### 变更目的
为了支持幂等性校验以及外部业务透传（例如日历签到场景下，每天只能签到一次，使用日期作为业务ID），引入了 `out_business_no` 字段。

### 变更内容
*   **新增字段**: `out_business_no` (varchar 64) - 业务仿重ID，外部透传，方便查询使用。
*   **修改字段含义**: `biz_id` 现在通常由 `out_business_no` + `rebate_type` 等组合而成，保证全局唯一性。

### 表结构定义 (部分)
```sql
CREATE TABLE `user_behavior_rebate_order_000` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `user_id` varchar(32) NOT NULL COMMENT '用户ID',
  `order_id` varchar(12) NOT NULL COMMENT '订单ID',
  `behavior_type` varchar(16) NOT NULL COMMENT '行为类型（sign 签到、openai_pay 支付）',
  `rebate_desc` varchar(128) NOT NULL COMMENT '返利描述',
  `rebate_type` varchar(16) NOT NULL COMMENT '返利类型（sku 活动库存充值商品、integral 用户活动积分）',
  `rebate_config` varchar(32) NOT NULL COMMENT '返利配置【sku值，积分值】',
  `out_business_no` varchar(64) NOT NULL COMMENT '业务仿重ID - 外部透传，方便查询使用',
  `biz_id` varchar(128) NOT NULL COMMENT '业务ID - 拼接的唯一值。拼接 out_business_no + 自身枚举',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_id` (`order_id`),
  UNIQUE KEY `uq_biz_id` (`biz_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为返利流水订单表';
```
