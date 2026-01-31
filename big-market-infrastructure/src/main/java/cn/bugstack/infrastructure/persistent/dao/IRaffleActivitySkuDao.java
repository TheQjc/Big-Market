package cn.bugstack.infrastructure.persistent.dao;

import cn.bugstack.infrastructure.persistent.po.RaffleActivitySku;

/**
 * @author Niudeyipi @TheQjc
 * @description 商品sku dao
 * @create 2026-01-31 21:01
 */
public interface IRaffleActivitySkuDao {
    // 根据sku查询活动sku
    RaffleActivitySku queryActivitySku(Long skuId);
}
