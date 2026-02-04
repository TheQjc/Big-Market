# Activity 领域模块深度分析

> **路径**: `big-market-domain/src/main/java/cn/bugstack/domain/activity`  
> **核心功能**: 抽奖活动订单创建与账户额度管理

---

## 一、目录结构总览

```
activity/
├── model/                          # 领域模型层
│   ├── aggregate/                  # 聚合根
│   │   └── CreateOrderAggregate.java
│   ├── entity/                     # 实体对象
│   │   ├── ActivityAccountEntity.java
│   │   ├── ActivityCountEntity.java
│   │   ├── ActivityEntity.java
│   │   ├── ActivityOrderEntity.java
│   │   ├── ActivitySkuEntity.java
│   │   └── SkuRechargeEntity.java
│   └── valobj/                     # 值对象
│       ├── ActivityStateVO.java
│       └── OrderStateVO.java
├── repository/                     # 仓储接口
│   └── IActivityRepository.java
└── service/                        # 领域服务
    ├── IRaffleOrder.java           # 服务接口
    ├── RaffleActivitySupport.java  # 支撑类
    ├── AbstractRaffleActivity.java # 抽象模板类
    ├── RaffleActivityService.java  # 具体实现类
    └── rule/                       # 规则责任链
        ├── IActionChain.java
        ├── IActionChainArmory.java
        ├── AbstractActionChain.java
        ├── factory/
        │   └── DefaultActivityChainFactory.java
        └── impl/
            ├── ActivityBaseActionChain.java
            └── ActivitySkuStockActionChain.java
```

---

## 二、DDD 与 MVC 架构对比

### 2.1 核心差异

| 维度 | MVC 模式 | DDD 模式 (本项目) |
|------|----------|-------------------|
| **Service层** | 一个类包含所有业务逻辑 | 接口 + 抽象类 + 实现类 多层封装 |
| **数据访问** | Service 直接调用 DAO/Mapper | 通过 Repository 接口隔离 |
| **业务规则** | if-else 硬编码在 Service | 责任链/策略模式解耦 |
| **数据模型** | DTO/VO 混用 | Entity/VO/Aggregate 严格区分 |
| **事务边界** | Service 方法级别 | 聚合根级别 |

### 2.2 本项目 Service 层的封装层次

```
┌─────────────────────────────────────────────────────────────┐
│                     IRaffleOrder                            │  ← 接口层：定义契约
│  + createSkuRechargeOrder(SkuRechargeEntity): String        │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ implements
┌─────────────────────────────────────────────────────────────┐
│                 AbstractRaffleActivity                      │  ← 模板层：定义流程骨架
│  + createSkuRechargeOrder()  ← 模板方法（final语义）         │
│  # buildOrderAggregate()     ← 抽象方法（子类实现）          │
│  # doSaveOrder()             ← 抽象方法（子类实现）          │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
┌─────────────────────────────────────────────────────────────┐
│                  RaffleActivitySupport                      │  ← 支撑层：封装通用查询
│  # activityRepository                                       │
│  # defaultActivityChainFactory                              │
│  + queryActivitySku()                                       │
│  + queryRaffleActivityByActivityId()                        │
│  + queryRaffleActivityCountByActivityCountId()              │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
┌─────────────────────────────────────────────────────────────┐
│                  RaffleActivityService                      │  ← 实现层：具体业务逻辑
│  @Service                                                   │
│  + buildOrderAggregate()   ← 实现订单构建                    │
│  + doSaveOrder()           ← 实现订单保存                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 为什么要这样分层？

```java
// ❌ MVC 风格：所有逻辑堆在一个方法里
@Service
public class ActivityService {
    public String createOrder(SkuRechargeEntity entity) {
        // 1. 参数校验 (10行)
        // 2. 查询数据 (20行)
        // 3. 规则校验 (30行 if-else)
        // 4. 构建订单 (20行)
        // 5. 保存订单 (10行)
        // 总计：100行混在一起，难以维护
    }
}

// ✅ DDD 风格：职责分离，层层封装
// 接口定义契约
public interface IRaffleOrder {
    String createSkuRechargeOrder(SkuRechargeEntity entity);
}

// 抽象类定义流程骨架（模板方法）
public abstract class AbstractRaffleActivity {
    public String createSkuRechargeOrder(SkuRechargeEntity entity) {
        // 步骤1-6 固定流程
        // 具体实现延迟到子类
    }
    protected abstract CreateOrderAggregate buildOrderAggregate(...);
    protected abstract void doSaveOrder(...);
}

// 具体类只关注"怎么做"
public class RaffleActivityService extends AbstractRaffleActivity {
    // 只实现 buildOrderAggregate 和 doSaveOrder
}
```

**优势**：
1. **单一职责**：每个类只做一件事
2. **开闭原则**：新增业务只需继承抽象类，不修改现有代码
3. **可测试性**：可以单独测试每一层
4. **可复用性**：Support 类的查询方法可被多个子类复用

---

## 三、责任链模式详解

### 3.1 责任链结构

```
┌─────────────────────────────────────────────────────────────┐
│                   IActionChainArmory                        │  ← 链式装配接口
│  + next(): IActionChain                                     │
│  + appendNext(IActionChain): IActionChain                   │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
┌─────────────────────────────────────────────────────────────┐
│                      IActionChain                           │  ← 规则执行接口
│  + action(ActivitySkuEntity, ActivityEntity,                │
│           ActivityCountEntity): boolean                     │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ implements
┌─────────────────────────────────────────────────────────────┐
│                   AbstractActionChain                       │  ← 抽象基类
│  - next: IActionChain                                       │
│  + next(): IActionChain                                     │
│  + appendNext(IActionChain): IActionChain                   │
└─────────────────────────────────────────────────────────────┘
                              ▲
               ┌──────────────┴──────────────┐
               │                             │
┌──────────────────────────┐   ┌──────────────────────────┐
│  ActivityBaseActionChain │   │ ActivitySkuStockActionChain│
│  @Component("activity_   │   │  @Component("activity_   │
│    base_action")         │   │    sku_stock_action")    │
│  校验：有效期、状态       │   │  校验：SKU库存           │
└──────────────────────────┘   └──────────────────────────┘
```

### 3.2 接口职责分离

```java
// 装配接口：负责链的组装
public interface IActionChainArmory {
    IActionChain next();                        // 获取下一个节点
    IActionChain appendNext(IActionChain next); // 追加下一个节点
}

// 执行接口：负责业务规则
public interface IActionChain extends IActionChainArmory {
    boolean action(ActivitySkuEntity sku, 
                   ActivityEntity activity, 
                   ActivityCountEntity count);
}
```

**为什么要分两个接口？**
- `IActionChainArmory`：关注**链的结构**（如何组装）
- `IActionChain`：关注**业务逻辑**（如何执行）
- 符合**接口隔离原则**，职责单一

### 3.3 抽象基类实现

```java
public abstract class AbstractActionChain implements IActionChain {

    private IActionChain next;  // 持有下一个节点的引用

    @Override
    public IActionChain next() {
        return next;
    }

    @Override
    public IActionChain appendNext(IActionChain next) {
        this.next = next;
        return next;  // 返回 next 支持链式调用：a.appendNext(b).appendNext(c)
    }
    
    // action() 由子类实现具体规则
}
```

### 3.4 工厂类：链的组装

```java
@Service
public class DefaultActivityChainFactory {

    private final IActionChain actionChain;  // 链的头节点

    /**
     * 构造函数中完成链的组装
     * Spring 自动将所有 IActionChain 实现注入到 Map 中
     * key = Bean 名称，value = Bean 实例
     */
    public DefaultActivityChainFactory(Map<String, IActionChain> actionChainGroup) {
        // 获取第一个节点
        actionChain = actionChainGroup.get("activity_base_action");
        // 链接第二个节点
        actionChain.appendNext(actionChainGroup.get("activity_sku_stock_action"));
        
        // 如需扩展，继续 appendNext：
        // .appendNext(actionChainGroup.get("activity_xxx_action"));
    }

    // 对外暴露链的入口
    public IActionChain openActionChain() {
        return this.actionChain;
    }
}
```

**Spring 的魔法**：`Map<String, IActionChain>` 会自动收集所有实现了 `IActionChain` 的 Bean。

### 3.5 具体节点实现

```java
// 节点1：基础信息校验
@Slf4j
@Component("activity_base_action")  // Bean 名称对应枚举 code
public class ActivityBaseActionChain extends AbstractActionChain {

    @Override
    public boolean action(ActivitySkuEntity sku, 
                          ActivityEntity activity, 
                          ActivityCountEntity count) {
        log.info("活动责任链-基础信息【有效期、状态】校验开始。");
        
        // TODO: 实现具体校验逻辑
        // if (activity.getState() != ActivityStateVO.open) throw ...
        // if (now < activity.getBeginDateTime()) throw ...
        
        // 传递给下一个节点
        return next().action(sku, activity, count);
    }
}

// 节点2：库存校验（链的末端）
@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySkuStockActionChain extends AbstractActionChain {

    @Override
    public boolean action(ActivitySkuEntity sku, 
                          ActivityEntity activity, 
                          ActivityCountEntity count) {
        log.info("活动责任链-商品库存处理【校验&扣减】开始。");
        
        // TODO: 实现库存扣减逻辑
        
        // 链的末端，直接返回结果
        return true;
    }
}
```

### 3.6 责任链执行流程

```
调用入口
    │
    ▼
┌─────────────────────────────────────────┐
│  defaultActivityChainFactory            │
│      .openActionChain()                 │
│      .action(sku, activity, count)      │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│  ActivityBaseActionChain.action()       │
│  1. 校验活动有效期                       │
│  2. 校验活动状态                         │
│  3. return next().action(...)  ────────────┐
└─────────────────────────────────────────┘  │
                                             ▼
                        ┌─────────────────────────────────────────┐
                        │  ActivitySkuStockActionChain.action()   │
                        │  1. 校验SKU库存                          │
                        │  2. 扣减库存                             │
                        │  3. return true （链末端）               │
                        └─────────────────────────────────────────┘
```

### 3.7 如何扩展新规则？

```java
// 1. 新增节点类
@Slf4j
@Component("activity_user_limit_action")
public class ActivityUserLimitActionChain extends AbstractActionChain {
    @Override
    public boolean action(...) {
        // 校验用户参与次数限制
        return next().action(...);
    }
}

// 2. 在工厂中添加到链
public DefaultActivityChainFactory(Map<String, IActionChain> actionChainGroup) {
    actionChain = actionChainGroup.get("activity_base_action");
    actionChain.appendNext(actionChainGroup.get("activity_user_limit_action"))  // 新增
               .appendNext(actionChainGroup.get("activity_sku_stock_action"));
}

// 3. 枚举中添加 code
public enum ActionModel {
    activity_base_action("activity_base_action", "..."),
    activity_user_limit_action("activity_user_limit_action", "用户参与次数限制"),  // 新增
    activity_sku_stock_action("activity_sku_stock_action", "...");
}
```

---

## 四、领域模型详解

### 4.1 Entity（实体）

| 类名 | 作用 | 关键字段 |
|------|------|----------|
| `SkuRechargeEntity` | 充值请求入参 | userId, sku, outBusinessNo |
| `ActivityEntity` | 活动信息 | activityId, activityName, beginDateTime, endDateTime |
| `ActivitySkuEntity` | SKU商品信息 | sku, activityId, activityCountId, stockCount |
| `ActivityCountEntity` | 次数配置 | totalCount, dayCount, monthCount |
| `ActivityOrderEntity` | 订单信息 | orderId, userId, sku, state, outBusinessNo |
| `ActivityAccountEntity` | 账户额度 | totalCount, totalCountSurplus, dayCount... |

### 4.2 ValueObject（值对象）

```java
// 值对象特点：无唯一标识，通过值来判断相等性
@Getter
@AllArgsConstructor
public enum OrderStateVO {
    completed("completed", "完成");
    
    private final String code;
    private final String desc;
}
```

### 4.3 Aggregate（聚合根）

```java
/**
 * 聚合根：保证事务一致性的边界
 * 包含：订单 + 账户额度变更信息
 */
@Data
@Builder
public class CreateOrderAggregate {
    private String userId;
    private Long activityId;
    private Integer totalCount;      // 增加的总次数
    private Integer dayCount;        // 增加的日次数
    private Integer monthCount;      // 增加的月次数
    private ActivityOrderEntity activityOrderEntity;
}
```

**聚合根的作用**：
- 将"订单创建"和"账户充值"封装在一起
- 仓储层接收聚合根，在**同一个事务**中完成两个操作
- 保证数据一致性

---

## 五、完整业务流程

```java
// AbstractRaffleActivity.createSkuRechargeOrder()

public String createSkuRechargeOrder(SkuRechargeEntity skuRechargeEntity) {
    
    // ==================== 第1步：参数校验 ====================
    String userId = skuRechargeEntity.getUserId();
    Long sku = skuRechargeEntity.getSku();
    String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
    if (null == sku || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
    }

    // ==================== 第2步：查询基础信息 ====================
    // 2.1 SKU → 活动ID + 次数配置ID
    ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
    // 2.2 活动ID → 活动详情
    ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
    // 2.3 次数配置ID → 具体次数
    ActivityCountEntity activityCountEntity = queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

    // ==================== 第3步：规则校验（责任链）====================
    IActionChain actionChain = defaultActivityChainFactory.openActionChain();
    boolean success = actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);

    // ==================== 第4步：构建聚合对象（子类实现）====================
    CreateOrderAggregate createOrderAggregate = buildOrderAggregate(
        skuRechargeEntity, activitySkuEntity, activityEntity, activityCountEntity
    );

    // ==================== 第5步：保存订单（子类实现）====================
    doSaveOrder(createOrderAggregate);

    // ==================== 第6步：返回订单号 ====================
    return createOrderAggregate.getActivityOrderEntity().getOrderId();
}
```

---

## 六、与 MVC 的核心差异总结

| 特性 | MVC | DDD (本项目) |
|------|-----|--------------|
| **Service 结构** | 单一类 | 接口 → 抽象类 → 支撑类 → 实现类 |
| **业务流程** | 过程式代码 | 模板方法模式 |
| **规则校验** | if-else 堆叠 | 责任链模式 |
| **数据模型** | DTO 一把梭 | Entity + VO + Aggregate 分层 |
| **数据访问** | 直接注入 Mapper | 通过 Repository 接口 |
| **事务边界** | @Transactional | 聚合根 + 编程式事务 |
| **扩展方式** | 修改原有代码 | 新增子类/链节点 |

---

## 七、类关系图

```
                              ┌─────────────────┐
                              │  IRaffleOrder   │
                              │   (interface)   │
                              └────────▲────────┘
                                       │
                              ┌────────┴────────┐
                              │ AbstractRaffle  │
                              │    Activity     │◆────────────────────┐
                              │  (abstract)     │                     │
                              └────────▲────────┘                     │
                                       │                              │
                              ┌────────┴────────┐         ┌──────────▼──────────┐
                              │ RaffleActivity  │         │ DefaultActivity     │
                              │    Support      │         │   ChainFactory      │
                              └────────▲────────┘         └──────────┬──────────┘
                                       │                              │
                              ┌────────┴────────┐                     │ uses
                              │ RaffleActivity  │         ┌──────────▼──────────┐
                              │    Service      │         │    IActionChain     │
                              │   (@Service)    │         │     (interface)     │
                              └─────────────────┘         └──────────▲──────────┘
                                       │                              │
                                       │ uses                         │ implements
                              ┌────────▼────────┐         ┌──────────┴──────────┐
                              │ IActivity       │         │  AbstractAction     │
                              │  Repository     │         │      Chain          │
                              │  (interface)    │         └──────────▲──────────┘
                              └─────────────────┘                    │
                                                          ┌─────────┴─────────┐
                                                          │                   │
                                                ┌─────────┴───────┐ ┌─────────┴───────┐
                                                │ ActivityBase    │ │ ActivitySkuStock│
                                                │  ActionChain    │ │   ActionChain   │
                                                └─────────────────┘ └─────────────────┘
```

---

## 八、学习建议

1. **先理解模板方法**：`AbstractRaffleActivity.createSkuRechargeOrder()` 是入口
2. **再看责任链**：从 `DefaultActivityChainFactory` 开始，追踪链的组装和执行
3. **对比 MVC**：思考如果用 MVC 写同样功能，代码会是什么样
4. **实践扩展**：尝试新增一个责任链节点，体会"对扩展开放"
