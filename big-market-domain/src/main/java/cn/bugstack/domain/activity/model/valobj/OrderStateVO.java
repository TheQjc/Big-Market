package cn.bugstack.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Niudeyipi @TheQjc
 * @description 订单状态值对象
 * @create 2026-02-01 17:47
 */
@Getter
@AllArgsConstructor
public enum OrderStateVO {

    completed("completed", "完成");

    private final String code;

    private final String desc;
}
