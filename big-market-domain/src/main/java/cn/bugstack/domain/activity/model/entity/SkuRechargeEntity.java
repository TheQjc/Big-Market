package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Niudeyipi @TheQjc
 * @description 活动商品充值实体对象
 * @create 2026-02-01 17:39
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkuRechargeEntity {
    /*用户id*/
    private String userId;
    /*商品sku - activity + activity count*/
    private Long sku;
    /*幂等业务单号，外部谁充值谁透传，这样来保证幂等*/
    private String outBusinessNo;
}
