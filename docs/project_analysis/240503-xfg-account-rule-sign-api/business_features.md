# 业务功能变更说明

本次更新主要集中在前端接口能力的增强，包括日历签到状态查询、用户活动账户查询以及抽奖策略权重规则的展示。

## 1. 签到返利状态查询 (`is_calendar_sign_rebate`)
*   **功能**: 查询当前用户在今日是否已经完成了签到。
*   **入口**: `RaffleActivityController.isCalendarSignRebate`
*   **HTTP**: `POST /api/v1/raffle/activity/is_calendar_sign_rebate`
*   **逻辑**:
    1.  接收 `userId`。
    2.  生成当天的日期字符串作为 `outBusinessNo` (例如: "2024-05-03")。
    3.  调用 `behaviorRebateService.queryOrderByOutBusinessNo` 查询是否存在对应的返利单。
    4.  如果存在返利单，说明今日已签到。

## 2. 用户活动账户查询 (`query_user_activity_account`)
*   **功能**: 查询用户在特定活动下的账户额度信息（总额度、月额度、日额度）。
*   **入口**: `RaffleActivityController.queryUserActivityAccount`
*   **HTTP**: `POST /api/v1/raffle/activity/query_user_activity_account`
*   **逻辑**:
    1.  接收 `userId` 和 `activityId`。
    2.  调用 `raffleActivityAccountQuotaService.queryActivityAccountEntity`。
    3.  聚合 `ActivityAccountEntity` 中的 `Total`, `Month`, `Day` 的总次数和剩余次数。
    4.  返回 `UserActivityAccountResponseDTO` 对象。

## 3. 抽奖策略权重规则查询 (`query_raffle_strategy_rule_weight`)
*   **功能**: 展示抽奖活动的权重规则，例如“抽奖6000次必中奖X范围”。用于前端展示进度条或规则提示。
*   **入口**: `RaffleStrategyController.queryRaffleStrategyRuleWeight`
*   **HTTP**: `POST /api/v1/raffle/strategy/query_raffle_strategy_rule_weight`
*   **逻辑**:
    1.  接收 `userId` 和 `activityId`。
    2.  查询用户在该活动下的总参与次数 (`userActivityAccountTotalUseCount`)。
    3.  调用 `raffleRule.queryAwardRuleWeightByActivityId` 查询该活动配置的权重规则 (`rule_weight`)。
    4.  构建响应列表，每一项包含：
        *   `ruleWeightCount`: 触发该规则需要的权重值（如消耗积分数或抽奖次数）。
        *   `userActivityAccountTotalUseCount`: 用户当前的累计值。
        *   `strategyAwards`: 该权重等级下可解锁的奖品列表。

## 4. 领域模型更新
*   **UserBehaviorRebateOrder**: 增加了 `out_business_no` 字段，支持基于外部业务ID的防重查询。
*   **StrategyRepository**: 增加了解析 `rule_weight` 配置的逻辑，将字符串配置（如 `4000:102,103`）转换为实体对象。
