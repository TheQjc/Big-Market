package cn.bugstack.infrastructure.persistent.po;

import lombok.Data;

import java.util.Date;

/**
 * @author Niudeyipi @TheQjc
 * @description 抽奖活动SKU表 持久化对象
 * @create 2026-01-31 20:44
 */
@Data
public class RaffleActivitySku {

    private Long id;

    /**
     * 商品SKU
     */
    private Long sku;

    /**
     * 抽奖活动ID
     */
    private Long activityId;

    /**
     * 活动个人参与次数id
     */
    private Long activityCountId;

    /**
     * 库存总量
     */
    private Integer stockCount;
    /**
     * 剩余库存
     */
    private Integer stockCountSurplus;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;

}
