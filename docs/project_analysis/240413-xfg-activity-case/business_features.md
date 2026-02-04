# 抽奖活动流程串联 - 业务功能

本次提交实现了完整的"活动抽奖"全链路，将**活动**、**策略**、**发奖**三大领域有机串联起来，形成了一个端到端的业务闭环。

## 1. 核心业务流程

### 1.1 完整抽奖链路 (Activity Draw Flow)
入口: `RaffleActivityController#draw`

**流程步骤**:
1.  **参数校验**: 验证 `userId` 和 `activityId`。
2.  **参与活动**: 调用 `IRaffleActivityPartakeService#createOrder`
    *   扣减用户账户额度（总/月/日三级）。
    *   生成抽奖单 `UserRaffleOrder` (状态: `create`)。
    *   如果存在未使用的抽奖单，直接返回（幂等性保障）。
3.  **执行抽奖**: 调用 `IRaffleStrategy#performRaffle`
    *   使用抽奖单中的 `strategyId` 进行策略抽奖。
    *   返回中奖结果 `RaffleAwardEntity`（包含 `awardId`, `awardTitle`）。
4.  **记录中奖**: 调用 `IAwardService#saveUserAwardRecord`
    *   插入 `user_award_record` (中奖记录)。
    *   插入 `task` (发奖MQ任务)。
    *   **关键**: 更新 `user_raffle_order` 状态为 `used`，防止重复抽奖。
    *   三者在同一事务中，保证原子性。
5.  **返回结果**: 封装 `ActivityDrawResponseDTO` 返回给用户。

### 1.2 活动装配 (Activity Armory)
入口: `RaffleActivityController#armory`

**目的**: 数据预热，将活动和策略配置加载到 Redis 缓存。

**实现**:
*   **活动装配**: `IActivityArmory#assembleActivitySkuByActivityId`
    *   查询该活动下的所有 SKU。
    *   将每个 SKU 的库存预热到 Redis。
    *   预热活动配置和次数配置。
*   **策略装配**: `IStrategyArmory#assembleLotteryStrategyByActivityId`
    *   根据 `activityId` 反查 `strategyId`（新增的 `uq_strategy_id` 约束使其成为可能）。
    *   装配策略和奖品配置到缓存。

## 2. 关键技术实现

### 2.1 防重复抽奖机制
**问题**: 用户可能利用网络延迟或并发请求，使用同一个抽奖单多次抽奖。

**解决方案**:
*   在 `AwardRepository#saveUserAwardRecord` 中，增加了对 `user_raffle_order` 状态的原子更新：
    ```java
    int count = userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrderReq);
    if (1 != count) {
        status.setRollbackOnly();
        throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR);
    }
    ```
*   **原理**: 更新语句的 WHERE 条件为 `order_state = 'create'`，如果订单已被使用，更新行数为 0，触发回滚。

### 2.2 次数锁规则查询优化
在 `RuleLockLogicTreeNode` 中，之前硬编码的 `userRaffleCount = 10L` 被替换为动态查询：
*   **实现**: `repository.queryTodayUserRaffleCount(userId, strategyId)`
*   **逻辑**: 
    1.  根据 `strategyId` 反查 `activityId`（利用新增的唯一约束）。
    2.  查询 `raffle_activity_account_day` 表中该用户当天的参与次数。
    3.  计算公式: `dayCount - dayCountSurplus` = 已参与次数。

### 2.3 领域重构与接口演进
*   **接口重命名**:
    *   `IRaffleService` -> `IRaffleStrategyService`
    *   `RaffleController` -> `RaffleStrategyController`
    *   新增 `IRaffleActivityService` 和 `RaffleActivityController`
*   **目的**: 明确了"策略抽奖"与"活动抽奖"的边界，前者是纯粹的算法逻辑，后者是完整的业务流程。

## 3. 架构亮点

### 3.1 领域驱动设计 (DDD) 应用
*   **分层清晰**: Controller (触发层) -> Service (应用服务) -> Domain (领域模型) -> Repository (仓储)。
*   **无 Application/Case 层**: 在 Controller 中直接编排多个领域服务（`IRaffleActivityPartakeService`, `IRaffleStrategy`, `IAwardService`），适合中小型系统。如果业务复杂度进一步提升，可引入 Use Case 层进行编排。

### 3.2 幂等性与事务一致性
*   **多级幂等**:
    1.  抽奖单幂等：查询是否存在 `create` 状态的订单。
    2.  发奖幂等：`user_award_record` 的 `order_id` 唯一索引 + 状态更新的原子性。
*   **事务边界**: 严格遵循"一个聚合一个事务"原则，`UserAwardRecordAggregate` 包含的所有数据变更在一个事务中完成。

### 3.3 数据预热策略
通过 `armory` 接口，系统管理员可以在活动开始前手动触发预热，避免首次访问时的缓存穿透，提升用户体验。

## 4. 业务价值

本次提交打通了从"用户点击抽奖按钮"到"系统记录中奖结果并发起发奖流程"的完整链路，是整个抽奖系统的核心主流程实现。
