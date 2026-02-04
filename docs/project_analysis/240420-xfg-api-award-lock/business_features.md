# 业务功能特性分析

## 1. 抽奖次数解锁奖品 (Award Lock Rule)

### 背景
为了增加抽奖活动的趣味性和用户的参与度，引入了“需抽奖N次才能解锁特定奖品”的规则。用户在未达到指定抽奖次数前，特定奖品处于锁定状态（灰色/不可中奖）。

### 实现流程
1.  **查询奖品列表 API (`/query_raffle_award_list`)**:
    *   入参变更为 `userId` 和 `activityId`，以便同时获取“活动配置”和“用户参与状态”。
2.  **获取规则配置**:
    *   系统根据 `activityId` 找到对应的策略。
    *   遍历策略下的奖品列表，读取 `rule_models` 字段。
    *   批量查询 `rule_lock` 类型的规则树节点，获取每个奖品的“解锁阈值”。
3.  **计算解锁状态**:
    *   查询 `raffle_activity_account_day` 表，计算用户当日已抽奖次数。
    *   **比对逻辑**:
        *   `isAwardUnlock`: `已抽次数 >= 解锁阈值` (如果未配置规则，默认 true)。
        *   `waitUnLockCount`: `解锁阈值 - 已抽次数` (若已解锁则为0)。
4.  **返回结果**:
    *   前端收到带有 `isAwardUnlock` 和 `waitUnLockCount` 的 DTO，进行相应的 UI 展示（如锁头图标、进度条）。

### API 变更
*   **Request**: `RaffleAwardListRequestDTO` 增加 `userId`, `activityId`。
*   **Response**: `RaffleAwardListResponseDTO` 增加 `awardRuleLockCount`, `isAwardUnlock`, `waitUnLockCount`。

## 2. Redis库存Key的过期策略优化

### 问题
在扣减库存时，为了防止超卖和保证数据一致性，系统会生成特定库存值的锁 Key (`strategy_award_count_key_{strategyId}_{awardId}_{surplus}`). 之前的实现中，这些 Key 可能没有明确的过期时间，或者过期时间不够精准。

### 优化方案
将活动的 `endDateTime` (结束时间) 贯穿整个调用链路：
`UserRaffleOrder` -> `RaffleFactor` -> `RaffleStrategy` -> `LogicTree` -> `StrategyRepository`

**具体实现**:
在 `StrategyRepository.subtractionAwardStock` 中：
```java
if (null != endDateTime) {
    long expireMillis = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
    redisService.setNx(lockKey, expireMillis, TimeUnit.MILLISECONDS);
}
```
*   **效果**: 库存锁 Key 会在活动结束后的1天自动过期。这既保证了活动期间的并发控制，又避免了 Redis 垃圾数据的无限堆积。

## 3. 标准化序列化配置

*   **Redis Codec**: 统一配置为 `JsonJacksonCodec.INSTANCE`。
*   **目的**: 解决 Redis 控制台中出现乱码的问题，方便开发调试和运维排查。

## 4. 逻辑完善点
*   **ActivityRepository**: 修复了库存扣减时 `surplus == 0` 的逻辑分支，确保最后一个库存扣减操作能正确完成并触发后续事件（如发送 MQ 消息）。
