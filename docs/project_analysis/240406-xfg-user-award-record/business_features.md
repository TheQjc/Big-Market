# 中奖记录与可靠消息投递 - 业务功能

本次提交实现了从“抽奖完成”到“中奖落库”再到“异步发奖”的完整闭环，重点保障了分布式环境下的数据一致性。

## 1. 核心业务流程

### 1.1 中奖结果落库 (Award Recording)
入口: `AwardService#saveUserAwardRecord`
*   **输入**: 用户ID、奖品信息、抽奖单ID。
*   **聚合处理**: 构建 `UserAwardRecordAggregate`，包含：
    *   `UserAwardRecordEntity`: 纯粹的中奖业务数据。
    *   `TaskEntity`: 描述“发送MQ通知”这一行为的任务数据。
*   **事务执行**:
    1.  插入 `user_award_record_00x`。
    2.  插入 `task`。
    3.  **注意**: 两者在同一事务中，遵循 ACID 原则。

### 1.2 异步消息发送 (Async Messaging)
流程在事务提交后触发：
1.  **即时发送**: 尝试直接调用 `EventPublisher` 发送 MQ 消息。
    *   如果成功 -> 更新 `task` 状态为 `completed`。
    *   如果失败 -> 更新 `task` 状态为 `fail` (记录日志，等待补偿)。

### 1.3 故障补偿机制 (Compensation Job)
任务: `SendMessageTaskJob`
*   **机制**: 这是一个定时任务 (cron: `0/5 * * * * ?`)。
*   **扫描**: 扫描所有分库中的 `task` 表，查找状态为 `fail` 或长时间未变更为 `completed` 的任务。
*   **执行**: 重新投递 MQ 消息，并根据结果更新状态。
*   **优势**: 即使应用崩溃或 MQ 宕机，只要数据库不丢且恢复，消息最终一定会被发送（**最终一致性**）。

## 2. 代码重构与优化

### 2.1 账户额度镜像同步
在 `ActivityRepository` 中完善了对 `raffle_activity_account` 的更新逻辑。
*   **问题**: 之前可能仅更新了 `_day` 或 `_month` 表，导致主表的镜像字段滞后。
*   **修复**: 拆分了 Mapper 中的更新语句 (`updateActivityAccountMonthSubtractionQuota` 等)，并在扣减日/月额度时，显式调用主表的更新方法，确保主表中的 `x_count_surplus` 始终准确。

## 3. 技术亮点

*   **本地消息表模式 (Local Transaction Table)**: 解决了分布式事务中经典的“数据库与MQ一致性”问题。
*   **DDD 聚合应用**: `UserAwardRecordAggregate` 清晰地界定了事务边界——“记录中奖”和“生成发奖任务”必须同生共死。
*   **分库分表路由**: 在定时任务 `SendMessageTaskJob` 中，显式处理了 `dbRouter.setDBKey(i)`，确保能遍历所有分库进行补偿扫描。
