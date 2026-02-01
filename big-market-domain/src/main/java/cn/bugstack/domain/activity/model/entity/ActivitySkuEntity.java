package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Niudeyipi @TheQjc
 * @description 活动sku实体对象
 * @create 2026-02-01 17:34
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuEntity {
    /*商品sku*/
    private Long sku;
    /*活动id*/
    private Long activityId;
    /*活动个人参与id;在这个活动上，一个人可以参与多少次活动（总、日、月）*/
    private Long activityCountId;
    /*库存总量*/
    private Integer stockCount;
    /*库存剩余*/
    private Integer stockCountSurplus;
}
