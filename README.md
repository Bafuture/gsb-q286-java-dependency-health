# 服务依赖健康评估与降级决策组件

Pair-wise GSB 标注任务仓库（第 17 批 / 286）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 组件说明

实现位于 `src/main/java/com/example/gsb/health/`，核心入口为 `HealthEngine`。

### 使用示例

```java
HealthEngine engine = HealthEngine.builder()
        .weights(HealthWeights.of(0.5, 0.3, 0.2)) // 错误率 / 响应时间 / 超时比例
        .latencyBands(200, 2000)                  // 延迟优良/劣化水位（毫秒）
        .degradeThreshold(60)                     // 降级阈值
        .recoverThreshold(80)                     // 恢复阈值（>= 降级阈值，形成迟滞）
        .consecutiveRecoveries(3)                 // 连续 3 次达标才解除降级
        .propagationHeadroom(10)                  // 传播余量
        .build();

engine.registerDependency("payment");
engine.registerDependency("orderSvc");
engine.registerCall("orderSvc", "payment");       // orderSvc 聚合调用 payment
engine.registerFeature("checkout", "orderSvc");   // checkout 依赖 orderSvc（间接依赖 payment）

EvaluationReport report = engine.evaluate(Map.of(
        "payment", CallMetrics.of(100, 95, 40, 1500)));
report.degradedFeatures();                        // 本轮应降级的功能（只含受影响链路）
engine.impactedFeaturesOf("payment");             // payment 的影响面清单
engine.auditEvents();                             // 降级/恢复审计记录
engine.stats();                                   // 依赖数、当前降级项、触发次数、平均恢复时间
```

### 健康评分规则

`score = 100 * (wErr * (1 - 错误率) + wLat * 延迟得分 + wTO * (1 - 超时比例)) / (wErr + wLat + wTO)`，
权重按权重之和归一。延迟得分两段线性：平均延迟 ≤ goodLatencyMs 记 1.0，≥ badLatencyMs 记 0.0，之间线性衰减。

### 传播规则

`有效分 = min(自身原始分, 每个直接下游的有效分 + propagationHeadroom)`。
即上游聚合服务的评分最多比其最差下游高 headroom 分；下游不健康会沿调用链向上拖拽
聚合服务的有效分，可能连带触发聚合服务自身的降级判定。依赖间调用边要求有向无环，
登记成环会被拒绝。

### 降级与恢复规则

- 有效分 < 降级阈值：该依赖进入降级，只降级其影响面内的功能（沿 功能→依赖→下游 可达的功能），不影响其它链路。
- 恢复防抖：处于降级状态的依赖，有效分需连续 N 次评估 ≥ 恢复阈值才解除降级；期间任何一次不达标都会清零连续计数。
- 恢复阈值 ≥ 降级阈值，构成迟滞区间，避免分数在阈值附近抖动导致频繁升降级。

### 审计与统计

每次降级/恢复都会记录 `AuditEvent`（时间、依赖、动作、当时有效分、判定依据、被影响功能）；
`HealthStats` 提供依赖总数、当前降级项、累计降级触发次数与历次“降级→恢复”的平均耗时。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们调用的下游很多，出问题时不知道该降级哪一个，经常一刀切把整条链路都关掉。请从零实现一个服务依赖健康评估与降级决策组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持登记依赖及其调用关系（哪些业务功能依赖哪些下游），构成依赖图；2) 支持健康评分：按错误率、响应时间与超时比例计算每个下游的健康分，权重可配置；3) 支持降级决策：健康分低于阈值时只降级受该依赖影响的功能，不影响其它链路，需给出影响面清单；4) 支持依赖传播：某个下游不健康时，其上游聚合服务的评分要随之下降，需说明传播规则；5) 支持恢复判定：连续若干次评分恢复才解除降级，避免抖动；6) 支持决策审计：记录每次降级与恢复的时间、依据与被影响的功能；7) 提供统计：依赖数、当前降级项、降级触发次数与平均恢复时间；8) 测试覆盖健康评分、影响面计算、传播规则、抖动抑制、决策审计与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
