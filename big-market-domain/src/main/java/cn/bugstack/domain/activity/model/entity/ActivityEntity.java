package cn.bugstack.domain.activity.model.entity;

import cn.bugstack.domain.activity.model.valobj.ActivityStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author Niudeyipi @TheQjc
 * @description 活动实体对象
 * @create 2026-02-01 17:22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityEntity {
    /*活动id*/
    private Long activityId;
    /*活动名称*/
    private String activityName;
    /*活动描述*/
    private String activityDesc;
    /*开始时间*/
    private Date beginDateTime;
    /*结束时间*/
    private Date endDateTime;
    /*活动参与次数配置*/
    private Long activityCountId;
    /*抽奖策略id*/
    private Long strategyId;
    /*活动状态*/
    private ActivityStateVO state;
}
