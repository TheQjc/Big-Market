# 业务功能特性分析

## 1. 用户行为返利入账 (Behavior Rebate)

### 背景
为了提高用户活跃度，系统需要奖励特定用户行为（如每日签到、外部支付）。奖励形式包括“增加抽奖活动库存（SKU）”或“增加账户积分”。

### 核心流程
1.  **接口调用**: 所有行为统一通过 `IBehaviorRebateService.createOrder` 接入。
2.  **配置匹配**: 根据入参 `behaviorType` (如 `sign`) 查询 `daily_behavior_rebate` 表，获取所有开启状态的返利配置（支持一种行为触发多种返利）。
3.  **幂等性设计**:
    *   构造 `bizId`：`userId + "_" + rebateType + "_" + outBusinessNo`。
    *   利用数据库唯一索引 (`biz_id`) 防止重复返利（例如防止同一天重复签到领奖）。
4.  **事务处理 (Transactional Outbox)**:
    *   开启事务。
    *   插入 `user_behavior_rebate_order` (返利流水)。
    *   插入 `task` (MQ消息任务)。
    *   提交事务。
5.  **消息通知**:
    *   事务提交后，尝试直接发送 MQ 消息 (`send_rebate`)。
    *   若发送失败，由定时任务扫描 `task` 表补发。

### 代码亮点
*   **BehaviorRebateAggregate**: 采用聚合根模式，封装了 Order 和 Task 实体，保证两者在 Repository 层的一致性写入。
*   **Strategy Pattern**: 通过 `BehaviorTypeVO` 枚举管理行为类型，易于扩展（目前支持 `SIGN`, `OPENAI_PAY`）。

## 2. 任务调度分库扫描 (Job Sharding)

### 问题
原有的 `SendMessageTaskJob` 在处理分库分表环境下的定时任务时，逻辑可能存在瓶颈或上下文切换问题。

### 优化
*   **拆分执行**: 将任务拆分为 `exec_db01`, `exec_db02` 等独立方法。
*   **显式路由**: 对应不同的分库 (`dbRouter.setDBKey(1)`, `dbRouter.setDBKey(2)`)。
*   **优势**: 允许针对不同数据库实例配置不同的调度策略或线程池资源，隔离故障影响。

## 3. 账户额度初始化 Bug 修复

### 问题
在 `RaffleActivityPartakeService` 中，当用户首次在某月或某日参与活动创建账户记录时，初始剩余额度 (`Surplus`) 错误地使用了账户当前剩余额度，而不是账户总额度 (`Count`)。

### 修复
*   **Before**: `setMonthCountSurplus(activityAccountEntity.getMonthCountSurplus())` (错误：如果是新月，Surplus可能已经被扣减过或不正确)
*   **After**: `setMonthCountSurplus(activityAccountEntity.getMonthCount())` (正确：新周期开始，剩余额度应等于总额度上限)

## 4. 其它优化
*   **拼写修正**: `BackListLogicChain` -> `BlackListLogicChain`。
*   **SQL更新**: 同步了最新的测试数据和表结构。
