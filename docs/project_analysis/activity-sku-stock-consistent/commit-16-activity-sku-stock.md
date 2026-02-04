# 第16节：引入MQ处理活动SKU库存一致性 - 代码阅读指南

> **Commit**: ccdd6d82a57bf9c505e7712428104af0872807f2  
> **Author**: 小傅哥  
> **Date**: 2024-03-30

---

## 一、本次提交概述

本次提交的核心目的是解决 **"高并发场景下的活动库存一致性"** 问题。在之前实现的活动充值流程中，库存扣减逻辑还是空白，本次提交完整实现了基于 Redis + MQ + MySQL 的库存扣减方案。

**核心方案说明**：
1.  **缓存预热**：将活动 SKU 库存预热到 Redis。
2.  **缓存扣减**：下单时先扣减 Redis 缓存 (`decr`)，利用 Redis 原子性保证不超卖。
3.  **异步同步**：库存扣减成功后，将消息推送到延迟队列 (Redisson DelayedQueue)，异步更新 MySQL 真实库存。
4.  **最终一致**：当缓存库存消耗殆尽时，发送 MQ 消息通知系统将数据库库存清零（兜底）。

### 业务流程图

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   SKU充值下单   │ ──▶  │  Redis缓存扣减  │ ──▶  │  Redisson队列   │
│ (createOrder)   │      │   (.decr)       │      │   (Delayed)     │
└─────────────────┘      └─────────────────┘      └─────────────────┘
                                  │                        │ 
                                  ▼                        ▼ 定时任务
                         ┌─────────────────┐      ┌─────────────────┐
                         │  MQ: 库存耗尽   │      │  MySQL库存递减  │
                         │   (Topic)       │      │  (Update -1)    │
                         └─────────────────┘      └─────────────────┘
                                  │                        
                                  ▼ 监听器
                         ┌─────────────────┐
                         │ MySQL库存清零   │
                         │ (Update = 0)    │
                         └─────────────────┘
```

---

## 二、配置与依赖变更

### 2.1 依赖引入
在 `pom.xml` 中引入了 RabbitMQ 依赖：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2.2 环境配置
在 `docker-compose-environment.yml` 中新增了 `rabbitmq` 容器配置，并在 `application-dev.yml` 中配置了连接信息和 Topic：

```yaml
spring:
  rabbitmq:
    topic:
      activity_sku_stock_zero: activity_sku_stock_zero
```

---

## 三、核心代码解读

### 3.1 预热：活动数据装配

**类**: `ActivityArmory` (新增，armory意为军械库/装备部，这里指预热装配)

这个类负责将数据库中的活动配置加载到 Redis 中。

```java
@Override
public boolean assembleActivitySku(Long sku) {
    // 1. 查询 SKU 信息
    ActivitySkuEntity activitySkuEntity = activityRepository.queryActivitySku(sku);
    // 2. 预热库存到 Redis (key: activity_sku_stock_count_key_{sku})
    cacheActivitySkuStockCount(sku, activitySkuEntity.getStockCount());
    
    // ... 预热其他信息
    return true;
}
```

### 3.2 规则执行：责任链完善

**类**: `ActivitySkuStockActionChain` (活动SKU库存规则节点)

之前的空实现，现在加入了真正的扣减逻辑。

```java
@Override
public boolean action(ActivitySkuEntity activitySkuEntity, ...) {
    // 1. 调用 Dispatch 扣减 Redis 库存
    boolean status = activityDispatch.subtractionActivitySkuStock(activitySkuEntity.getSku(), ...);
    
    // 2. 扣减成功，写入 Redisson 延迟队列 (异步更新数据库)
    if (status) {
        activityRepository.activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO.builder()
                .sku(activitySkuEntity.getSku())
                .activityId(activityEntity.getActivityId())
                .build());
        return true;
    }
    
    // 3. 扣减失败，抛出异常
    throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR);
}
```

### 3.3 核心实现：Redis 原子扣减与 MQ 发送

**类**: `ActivityRepository`

这是最关键的逻辑实现：

```java
@Override
public boolean subtractionActivitySkuStock(Long sku, String cacheKey, Date endDateTime) {
    // 1. Redis 原子递减
    long surplus = redisService.decr(cacheKey);
    
    if (surplus == 0) {
        // 2.1 库存刚好扣完：发送 MQ 消息 "库存没了"
        eventPublisher.publish(topic, eventMessage);
        return false; // 下回再来，这次先返回失败？(注：这里返回false意味着最后一件也没抢到？需结合业务看)
        // 勘误：surplus == 0 表示扣减后剩余为0，即这是最后一单，应该是成功的。
        // 但看代码逻辑，surplus==0时返回false，可能是设计为"预警线"或者这里有特殊逻辑
        // 再看代码：surplus == 0 时发消息，且返回 false。
        // 这意味着 Redis 中存的是 "可用库存"，decr 后为 0 说明本次请求把库存扣成 0 了。
        // 但这里如果返回 false，前面的责任链会报错。这部分逻辑值得 debug 确认。
        // 修正理解：通常 decr 返回后 >= 0 都算成功。
        // 这里 surplus == 0 时，代码发送了 MQ 消息，告诉系统清空数据库库存。
    } else if (surplus < 0) {
        // 2.2 库存已经是负数：恢复为 0，返回失败
        redisService.setAtomicLong(cacheKey, 0);
        return false;
    }

    // 3. 加锁兜底 (防止 Redis Key 丢失或异常恢复)
    // 将这个 "库存数值" 加锁，防止重复使用？
    String lockKey = cacheKey + Constants.UNDERLINE + surplus;
    Boolean lock = redisService.setNx(lockKey, ...);
    return lock;
}
```

### 3.4 异步更新：定时任务

**类**: `UpdateActivitySkuStockJob` (Trigger层)

```java
@Scheduled(cron = "0/5 * * * * ?")
public void exec() {
    // 从 Redisson 延迟队列中获取 SKU 扣减记录
    ActivitySkuStockKeyVO vo = skuStock.takeQueueValue();
    if (null == vo) return;
    
    // 执行 MySQL 更新： update raffle_activity_sku set stock_count_surplus = stock_count_surplus - 1
    skuStock.updateActivitySkuStock(vo.getSku());
}
```

### 3.5 兜底机制：MQ 消息监听

**类**: `ActivitySkuStockZeroCustomer` (Trigger层)

当 Redis 发现库存为 0 时发送 MQ，消费者收到消息后：
1.  直接将 MySQL 库存置为 0 (`clearActivitySkuStock`)。
2.  清空延迟队列 (`clearQueueValue`)，因为不需要再慢慢 -1 了。

---

## 四、基础设施层变更

### 4.1 Redis 服务升级
- 新增 `setNx` 方法，用于加锁逻辑。
- 引入 `RBlockingQueue` 和 `RDelayedQueue` 处理异步库存更新。

### 4.2 数据库 DAO
- `IRaffleActivitySkuDao` 新增：
    - `updateActivitySkuStock`: 库存 -1
    - `clearActivitySkuStock`: 库存清零

---

## 五、类图设计 (Partial)

```
┌─────────────────┐       ┌─────────────────┐
│ IActivityArmory │       │ IActivityDispatch│
│ (预热装配接口)    │       │ (调度扣减接口)   │
└────────▲────────┘       └────────▲────────┘
         │                         │
         └───────────┐   ┌─────────┘
                  ┌──┴───┴──┐
                  │ Activity│
                  │  Armory │
                  └───┬───┬─┘
                      │   │
           ┌──────────┘   └──────────┐
           ▼                         ▼
┌────────────────────┐    ┌────────────────────┐
│ IActivityRepository│◀───│ ActivityRepository │
│ (query/cache/decr) │    │ (Redis/DAO Impl)   │
└────────────────────┘    └────────────────────┘
```

---

## 六、总结

本次提交构建了一个经典的**高并发库存扣减模型**：
1.  **高性能**：完全依赖 Redis (`decr`) 进行实时扣减，无数据库压力。
2.  **高可用**：异步延迟队列 (`Lazy Update`) 处理数据库更新，流量削峰。
3.  **一致性**：通过 MQ 消息处理"售罄"临界点，强制数据库兜底清零，保证最终一致。
