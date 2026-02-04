# 数据库表关系及字段变更分析

## 1. 抽奖活动账户日表 (raffle_activity_account_day)

该表用于记录用户在特定活动上的每日参与情况。本次更新中，增加了对用户当日已参与次数的查询能力，用于支持“抽奖N次后解锁奖品”的业务逻辑。

| 字段 | 类型 | 描述 | 变更/说明 |
|---|---|---|---|
| user_id | VARCHAR | 用户ID | 联合主键 |
| activity_id | BIGINT | 活动ID | 联合主键 |
| day | VARCHAR | 日期（yyyy-mm-dd） | 联合主键 |
| day_count | INT | 当日总额度 | |
| day_count_surplus | INT | 当日剩余额度 | |

**新查询逻辑：**
```sql
select day_count - day_count_surplus 
from raffle_activity_account_day 
where user_id = #{userId} and activity_id = #{activityId} and day = #{day}
```
*   **用途**：计算用户当日已经抽奖的次数（`Total - Surplus`），用于和奖品的解锁阈值进行比较。

## 2. 规则树节点表 (rule_tree_node)

规则树节点表存储了具体的规则参数。本次更新主要关注 `rule_lock` 类型的节点，用于配置奖品解锁所需的抽奖次数。

| 字段 | 类型 | 描述 | 变更/说明 |
|---|---|---|---|
| tree_id | VARCHAR | 规则树ID | 关联 strategy_award.rule_models |
| rule_key | VARCHAR | 规则Key | 新增关注 `rule_lock` |
| rule_value | VARCHAR | 规则值 | 存储需要的解锁次数（如 "3" 表示需抽奖3次） |

**查询逻辑：**
```sql
select tree_id, rule_value 
from rule_tree_node 
where rule_key = 'rule_lock' 
  and tree_id in (tree_lock_1, tree_lock_2, ...)
```

## 3. 策略奖品表 (strategy_award)

| 字段 | 类型 | 描述 | 变更/说明 |
|---|---|---|---|
| strategy_id | BIGINT | 策略ID | |
| award_id | INT | 奖品ID | |
| rule_models | VARCHAR | 规则模型 | 存储关联的规则TreeID |

*   **关联关系**：`strategy_award.rule_models` 关联到 `rule_tree_node.tree_id`，从而将具体的奖品与解锁规则（次数）绑定。

## 4. 关系图谱

```mermaid
erDiagram
    RAFFLE_ACTIVITY_ACCOUNT_DAY {
        string user_id
        long activity_id
        string day
        int day_count
        int day_count_surplus
    }
    
    STRATEGY_AWARD {
        long strategy_id
        int award_id
        string rule_models
        int sort
    }
    
    RULE_TREE_NODE {
        string tree_id
        string rule_key
        string rule_value
    }

    STRATEGY_AWARD ||--o{ RULE_TREE_NODE : "rule_models -> tree_id (Unlock logic)"
    RAFFLE_ACTIVITY_ACCOUNT_DAY ..> STRATEGY_AWARD : "Compare (Used Count) vs (Rule Value)"
```
