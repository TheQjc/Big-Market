# 业务功能特性分析

## 1. 用户行为返利结算 (Rebate Settlement)

### 背景
在前序功能中，我们实现了“记录用户行为并生成返利订单”以及“发送MQ消息”。本节重点在于**消费MQ消息并实际执行返利入账**，即“结算”过程。

### 核心流程
1.  **触发**: 用户调用日历签到接口 (`/api/v1/raffle/activity/calendar_sign_rebate`)。
2.  **记录 & 发送**: `BehaviorRebateService` 创建返利单，发送 `send_rebate` 消息。
3.  **消费 & 结算**:
    *   **监听器**: `RebateMessageCustomer` 监听 `send_rebate` Topic。
    *   **过滤**: 目前仅处理 `rebate_type = sku` (活动库存充值) 的消息。
    *   **入账**: 调用 `RaffleActivityAccountQuotaService.createOrder` 进行额度充值。
    *   **幂等**: 利用 `bizId` (`userId + "_" + rebateType + "_" + outBusinessNo`) 在 `sku_recharge_order` 表中保证不重复充值。

### 账户额度一致性升级
在执行 `createOrder` 进行额度充值时，`ActivityRepository` 进行了逻辑增强：
*   **旧逻辑**: 仅更新/插入总账户 (`raffle_activity_account`)。
*   **新逻辑**:
    *   插入/更新总账户。
    *   **同步更新月账户**: `raffleActivityAccountMonthDao.addAccountQuota` (增加月度和剩余额度)。
    *   **同步更新日账户**: `raffleActivityAccountDayDao.addAccountQuota` (增加日度和剩余额度)。
*   **意义**: 确保了“充值/返利”获得的额度不仅在总账中体现，也能在日/月维度中正确累加，防止出现“有总额度但因日/月限额耗尽而无法抽奖”的逻辑矛盾（具体取决于业务定义，此处实现为同步增加）。

### Bug 修复
*   **BehaviorRebateService**: 修复了构建 MQ 消息时 `rebateType` 取值错误的问题（原取了 `behaviorType`），确保下游能正确识别返利类型（SKU vs 积分）。

## 2. API 接口
*   **日历签到**: `POST /api/v1/raffle/activity/calendar_sign_rebate`
    *   入参: `userId`
    *   逻辑: 构造 `SIGN` 行为实体，以 `yyyyMMdd` 为业务ID（每天只能签到一次），触发返利流程。
