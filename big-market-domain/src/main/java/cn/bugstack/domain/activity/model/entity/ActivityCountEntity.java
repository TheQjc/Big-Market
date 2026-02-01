package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Niudeyipi @TheQjc
 * @description 活动次数实体对象
 * @create 2026-02-01 17:19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityCountEntity {
    /*活动次数编号*/
    private Long activityCountId;

    /*总次数*/
    private Integer totalCount;

    /*月次数*/
    private Integer monthCount;

    /*日次数*/
    private Integer dayCount;
}
