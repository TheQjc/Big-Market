package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Niudeyipi @TheQjc
 * @description 活动账户实体对象
 * @create 2026-02-01 17:14
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityAccountEntity {
    /*用户id*/
    private String userId;
    /*活动id*/
    private Long activityId;
    /*总次数*/
    private Integer totalCount;
    /*总次数-剩余*/
    private Integer totalCountSurplus;
    /*日次数*/
    private Integer dayCount;
    /*日次数-剩余*/
    private Integer dayCountSurplus;
    /*月次数*/
    private Integer monthCount;
    /*月次数-剩余*/
    private Integer monthCountSurplus;
}
