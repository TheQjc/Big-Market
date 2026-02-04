# 第15节：抽奖活动流水入库 - 代码阅读指南

> **Commit**: 9fbb2fdfb5f808364e419e4de0f1ad0add9d33ea  
> **Author**: 小傅哥  
> **Date**: 2024-03-23

---

## 一、本次提交概述

本次提交实现了 **"SKU 充值订单创建与活动账户额度入库"** 功能。核心场景是：用户通过某种行为（签到、分享、积分兑换等）触发充值，系统为其创建活动订单并增加抽奖次数。

### 业务流程图

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   用户触发充值   │ ──▶  │  创建充值订单    │ ──▶  │  账户额度增加    │
│ (签到/分享等)    │      │  (幂等防重)      │      │  (日/月/总次数)  │
└─────────────────┘      └─────────────────┘      └─────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
   SkuRechargeEntity      RaffleActivityOrder      RaffleActivityAccount
   (userId, sku,          (订单流水记录)            (抽奖次数账户)
    outBusinessNo)
```

---

## 二、核心代码结构

### 2.1 分层架构视图

```
big-market-domain/                          # 领域层
├── activity/
│   ├── model/
│   │   ├── entity/
│   │   │   ├── SkuRechargeEntity.java      # [新增] 充值入参实体
│   │   │   └── ActivityOrderEntity.java    # [修改] 增加 sku, outBusinessNo
│   │   └── aggregate/
│   │       └── CreateOrderAggregate.java   # [修改] 聚合根，包含订单+额度
│   ├── repository/
│   │   └── IActivityRepository.java        # [修改] 新增 doSaveOrder 方法
│   └── service/
│       ├── IRaffleOrder.java               # [修改] 接口方法重命名
│       ├── AbstractRaffleActivity.java     # [重构] 模板方法定义流程
│       ├── RaffleActivityService.java      # [修改] 实现构建与保存
│       ├── RaffleActivitySupport.java      # [新增] 支撑类，封装查询
│       └── rule/                           # [新增] 活动规则责任链
│           ├── IActionChain.java
│           ├── AbstractActionChain.java
│           ├── factory/DefaultActivityChainFactory.java
│           └── impl/
│               ├── ActivityBaseActionChain.java
│               └── ActivitySkuStockActionChain.java

big-market-infrastructure/                   # 基础设施层
├── persistent/
│   ├── dao/
│   │   └── IRaffleActivityAccountDao.java  # [修改] 新增 insert, updateAccountQuota
│   ├── po/
│   │   └── RaffleActivityOrder.java        # [修改] 增加 sku, outBusinessNo
│   └── repository/
│       └── ActivityRepository.java         # [修改] 实现 doSaveOrder 事务逻辑

big-market-app/                              # 应用层
├── resources/mybatis/mapper/
│   ├── raffle_activity_order_mapper.xml    # [修改] 新增字段映射
│   └── raffle_activity_account_mapper.xml  # [修改] 新增 insert/update SQL
```

---

## 三、关键代码解读

### 3.1 入口：接口定义变更

**文件**: `IRaffleOrder.java`

```java
// 旧接口 (已删除)
ActivityOrderEntity createRaffleActivityOrder(ActivityShopCartEntity activityShopCartEntity);

// 新接口
String createSkuRechargeOrder(SkuRechargeEntity skuRechargeEntity);
```

**变化说明**:
- 入参从 `ActivityShopCartEntity` 改为 `SkuRechargeEntity`，新增了 `outBusinessNo` 字段用于幂等控制
- 返回值从完整订单实体改为订单ID字符串

---

### 3.2 核心：模板方法模式

**文件**: `AbstractRaffleActivity.java`

```java
@Override
public String createSkuRechargeOrder(SkuRechargeEntity skuRechargeEntity) {
    // 1. 参数校验
    if (null == sku || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
    }

    // 2. 查询基础信息 (通过 RaffleActivitySupport 父类)
    ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
    ActivityEntity activityEntity = queryRaffleActivityByActivityId(...);
    ActivityCountEntity activityCountEntity = queryRaffleActivityCountByActivityCountId(...);

    // 3. 活动规则校验 (责任链模式)
    IActionChain actionChain = defaultActivityChainFactory.openActionChain();
    actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

    // 4. 构建聚合对象 (抽象方法，子类实现)
    CreateOrderAggregate aggregate = buildOrderAggregate(...);

    // 5. 保存订单 (抽象方法，子类实现)
    doSaveOrder(aggregate);

    // 6. 返回订单号
    return aggregate.getActivityOrderEntity().getOrderId();
}

// 子类必须实现的抽象方法
protected abstract CreateOrderAggregate buildOrderAggregate(...);
protected abstract void doSaveOrder(CreateOrderAggregate createOrderAggregate);
```

**设计模式**: **模板方法模式 (Template Method)**
- 父类定义算法骨架（6个步骤）
- 子类 `RaffleActivityService` 实现具体的构建和保存逻辑

---

### 3.3 责任链：活动规则校验

**文件**: `DefaultActivityChainFactory.java`

```java
public DefaultActivityChainFactory(Map<String, IActionChain> actionChainGroup) {
    // Spring 自动注入所有 IActionChain 实现到 Map
    actionChain = actionChainGroup.get("activity_base_action");
    actionChain.appendNext(actionChainGroup.get("activity_sku_stock_action"));
}

// 责任链执行顺序:
// ActivityBaseActionChain (有效期/状态校验)
//     ↓
// ActivitySkuStockActionChain (SKU库存校验)
```

**责任链节点**:

| 节点 | Bean名称 | 职责 |
|------|----------|------|
| `ActivityBaseActionChain` | activity_base_action | 校验活动有效期、状态 |
| `ActivitySkuStockActionChain` | activity_sku_stock_action | 校验/扣减SKU库存 |

---

### 3.4 聚合根：CreateOrderAggregate

**文件**: `CreateOrderAggregate.java`

```java
public class CreateOrderAggregate {
    private String userId;              // 用户ID
    private Long activityId;            // 活动ID
    private Integer totalCount;         // 增加的总次数
    private Integer dayCount;           // 增加的日次数
    private Integer monthCount;         // 增加的月次数
    private ActivityOrderEntity activityOrderEntity;  // 订单实体
}
```

**作用**: 聚合根将"订单"和"账户额度变更"封装在一起，确保它们在同一个事务中完成。

---

### 3.5 持久化：事务与分库分表

**文件**: `ActivityRepository.doSaveOrder()`

```java
public void doSaveOrder(CreateOrderAggregate createOrderAggregate) {
    try {
        // 1. 设置分库分表路由 (基于 userId)
        dbRouter.doRouter(createOrderAggregate.getUserId());
        
        // 2. 编程式事务
        transactionTemplate.execute(status -> {
            try {
                // 2.1 写入订单
                raffleActivityOrderDao.insert(raffleActivityOrder);
                
                // 2.2 更新账户额度
                int count = raffleActivityAccountDao.updateAccountQuota(raffleActivityAccount);
                
                // 2.3 账户不存在则创建
                if (0 == count) {
                    raffleActivityAccountDao.insert(raffleActivityAccount);
                }
                return 1;
            } catch (DuplicateKeyException e) {
                // 幂等：outBusinessNo 唯一索引冲突
                status.setRollbackOnly();
                throw new AppException(ResponseCode.INDEX_DUP.getCode());
            }
        });
    } finally {
        // 3. 清理路由上下文
        dbRouter.clear();
    }
}
```

**关键点**:
1. **分库分表**: `dbRouter.doRouter(userId)` 确保同一用户的订单和账户在同一个库
2. **编程式事务**: 使用 `TransactionTemplate` 而非 `@Transactional`，便于与 db-router 配合
3. **幂等性**: `outBusinessNo` 字段加唯一索引，重复请求抛出 `INDEX_DUP` 异常

---

### 3.6 账户额度更新SQL

**文件**: `raffle_activity_account_mapper.xml`

```xml
<update id="updateAccountQuota">
    update raffle_activity_account
    set
        total_count = total_count + #{totalCount},
        total_count_surplus = total_count_surplus + #{totalCountSurplus},
        day_count = day_count + #{dayCount},
        day_count_surplus = day_count_surplus + #{dayCountSurplus},
        month_count = month_count + #{monthCount},
        month_count_surplus = month_count_surplus + #{monthCountSurplus},
        update_time = now()
    where user_id = #{userId} and activity_id = #{activityId}
</update>
```

**说明**: 使用 `+= ` 原子操作增加额度，而非先查后改，避免并发问题。

---

## 四、数据库表变更

### 4.1 raffle_activity_order 表

| 新增字段 | 类型 | 说明 |
|----------|------|------|
| `sku` | bigint | 商品SKU |
| `out_business_no` | varchar(64) | 业务防重ID |

**新增索引**: `uq_out_business_no` (唯一索引，实现幂等)

### 4.2 raffle_activity_account 表

无结构变更，补充了完整的字段映射。

---

## 五、类图关系

```
┌────────────────────────────────────────────────────────────────┐
│                        IRaffleOrder                            │
│  + createSkuRechargeOrder(SkuRechargeEntity): String           │
└────────────────────────────────────────────────────────────────┘
                              ▲
                              │ implements
┌────────────────────────────────────────────────────────────────┐
│                   AbstractRaffleActivity                       │
│  # buildOrderAggregate(): CreateOrderAggregate  «abstract»     │
│  # doSaveOrder(CreateOrderAggregate): void      «abstract»     │
│  + createSkuRechargeOrder(): String             «template»     │
└────────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
┌────────────────────────────────────────────────────────────────┐
│                   RaffleActivitySupport                        │
│  # activityRepository: IActivityRepository                     │
│  # defaultActivityChainFactory: DefaultActivityChainFactory    │
│  + queryActivitySku(Long): ActivitySkuEntity                   │
│  + queryRaffleActivityByActivityId(Long): ActivityEntity       │
└────────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
┌────────────────────────────────────────────────────────────────┐
│                    RaffleActivityService                       │
│  + buildOrderAggregate(): CreateOrderAggregate  «override»     │
│  + doSaveOrder(): void                          «override»     │
└────────────────────────────────────────────────────────────────┘
```

---

## 六、测试验证

**文件**: `RaffleOrderTest.java`

```java
@Test
public void test_createSkuRechargeOrder() {
    SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
    skuRechargeEntity.setUserId("xiaofuge");
    skuRechargeEntity.setSku(9011L);
    skuRechargeEntity.setOutBusinessNo("700091009111");  // 幂等ID
    
    String orderId = raffleOrder.createSkuRechargeOrder(skuRechargeEntity);
    log.info("测试结果：{}", orderId);
}
```

**幂等测试**: 同一个 `outBusinessNo` 再次调用会抛出 `Duplicate entry` 异常。

---

## 七、核心设计总结

| 设计点 | 实现方式 | 目的 |
|--------|----------|------|
| **模板方法** | AbstractRaffleActivity | 统一流程，子类专注实现 |
| **责任链** | IActionChain + Factory | 活动规则可扩展 |
| **聚合根** | CreateOrderAggregate | 保证事务一致性 |
| **编程式事务** | TransactionTemplate | 配合分库分表中间件 |
| **幂等控制** | outBusinessNo + 唯一索引 | 防止重复充值 |
| **分库分表** | mini-db-router | 用户维度数据隔离 |

---

## 八、后续扩展点

1. **责任链完善**: `ActivityBaseActionChain` 和 `ActivitySkuStockActionChain` 目前只打印日志，后续需实现真正的校验逻辑
2. **库存扣减**: SKU库存扣减可能需要引入 Redis 分布式锁或乐观锁
3. **异步化**: 大流量场景下可考虑将订单写入MQ异步处理
