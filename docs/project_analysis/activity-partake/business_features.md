# 活动参与模块 - 业务功能分析

## 1. 核心功能概述
本模块主要实现“用户参与抽奖活动”的核心流程。也就是用户点击“立即抽奖”时，系统内部发生的“验资”（额度扣减）与“出票”（生成订单）的过程。

功能入口：`IRaffleActivityPartakeService#createOrder`

## 2. 业务流程详情

### 2.1 参与活动流程 (Partake Process)
整个参与过程封装在 `AbstractRaffleActivityPartake` 模板模式中：

1.  **活动校验**：
    *   校验活动状态（必须为 `open`）。
    *   校验活动时间（当前时间必须在 `beginDateTime` 和 `endDateTime` 之间）。
2.  **幂等性检查 (Idempotency)**：
    *   查询是否存在**未被使用**（状态为 `create`）的抽奖活动订单。
    *   **策略**：如果存在，直接返回该订单，**不扣减额度**。这防止因网络抖动导致用户重复点击时的超卖或重复扣款。
3.  **额度账户过滤 (Account Quota Filtering)**：
    *   按顺序校验并构建账户对象：`总额度` -> `月额度` -> `日额度`。
    *   如果任一维度的剩余额度（Surplus）不足，抛出异常。
    *   如果是当月/当日首次参与，会自动初始化月/日账户实体。
4.  **构建订单聚合对象 (Aggregate Building)**：
    *   将 `User`、`Activity`、`Account` (总/月/日)、`Order` 封装为一个 **CreatePartakeOrderAggregate**。
5.  **事务落库 (Transactional Persistence)**：
    *   在一个数据库事务中完成：扣减所有层级额度 + 插入/更新月日账户 + 插入抽奖订单。

### 2.2 额度管理逻辑 (Quota Management)
系统采用了**三级额度模型**来精细控制用户的参与频率：
*   **总账户 (`ActivityAccountEntity`)**：控制用户在整个活动周期内的总参与次数。
*   **月账户 (`ActivityAccountMonthEntity`)**：控制每月的参与上限。
*   **日账户 (`ActivityAccountDayEntity`)**：控制每日的参与上限。

**扣减顺序**：
1.  扣减总账户 `total_count_surplus`。
2.  (如果存在月限制) 扣减/初始化月账户 `month_count_surplus` 并同步更新总账户中的 `month_count_surplus` 镜像字段。
3.  (如果存在日限制) 扣减/初始化日账户 `day_count_surplus` 并同步更新总账户中的 `day_count_surplus` 镜像字段。

### 2.3 分库分表支持
*   **路由决策**：所有数据库操作（账户更新、订单写入）在 `ActivityRepository` 中通过 `dbRouter.doRouter(userId)` 统一路由，确保同一个用户的操作落在一个库中，保证事务的 ACID 特性。

## 3. 特色功能分析

### 3.1 领域驱动设计 (DDD) 的应用
*   **聚合根 (Aggregate Root)**：`CreatePartakeOrderAggregate` 将原本分散的账户更新和订单创建行为聚合在一起，保证了业务规则的一致性。
*   **模板模式 (Template Pattern)**：`AbstractRaffleActivityPartake` 定义了标准的参与流程骨架，将具体的额度过滤逻辑 (`doFilterAccount`) 和订单构建逻辑 (`buildUserRaffleOrder`) 留给子类实现，便于扩展。

### 3.2 高并发与防超卖
*   **行级锁**：数据库更新语句 `updateActivityAccountSubtractionQuota` 隐含了行锁，保证并发下的扣减准确性。
*   **镜像额度**：在总表中冗余记录日/月剩余额度，虽然增加了更新成本，但在某些查询场景下可以避免联表查询，提升性能。

### 3.3 故障恢复与补偿
虽然本流程主要关注同步交易，但引入 `Data Base Router` 和 `TransactionTemplate` 确保了在分库分表环境下，单用户维度的操作具备强一致性。对于跨维度的操作（如发奖），则通过后续的 MQ + 任务表（Task）实现最终一致性。
