package com.example.gsb.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务依赖健康评估与降级决策引擎。
 *
 * 使用方式：
 *   1. registerDependency 登记下游；registerCall 登记下游间调用关系；
 *      registerFeature 登记业务功能依赖哪些下游。
 *   2. 每个评估周期调用 evaluate，传入本周期各下游的 {@link CallMetrics}。
 *   3. 通过 report / degradedFeatures / stats / auditEvents 获取决策与统计。
 *
 * 传播规则：有效分 = min(自身原始分, 每个直接下游的有效分 + propagationHeadroom)。
 * 即上游聚合服务的评分不会高于其最差下游超过 headroom 分，下游不健康会
 * 沿调用链向上拖拽聚合服务的评分，从而可能触发聚合服务自身的降级判定。
 *
 * 恢复规则（防抖）：处于降级状态的依赖，其有效分需连续
 * consecutiveRecoveriesRequired 次评估 >= recoverThreshold 才解除降级；
 * 期间任何一次低于 recoverThreshold 都会清零连续计数。recoverThreshold
 * 必须 >= degradeThreshold，形成迟滞区间，避免在阈值附近抖动。
 */
public final class HealthEngine {

    private final DependencyGraph graph = new DependencyGraph();
    private final HealthScorer scorer;
    private final double degradeThreshold;
    private final double recoverThreshold;
    private final int consecutiveRecoveriesRequired;
    private final double propagationHeadroom;
    private final Clock clock;

    private final Map<String, DependencyState> states = new LinkedHashMap<>();
    private final List<AuditEvent> auditLog = new ArrayList<>();
    private final List<Duration> recoveryDurations = new ArrayList<>();
    private long totalDegradations;

    private HealthEngine(Builder builder) {
        this.scorer = new HealthScorer(builder.weights, builder.goodLatencyMs, builder.badLatencyMs);
        this.degradeThreshold = builder.degradeThreshold;
        this.recoverThreshold = builder.recoverThreshold;
        this.consecutiveRecoveriesRequired = builder.consecutiveRecoveries;
        this.propagationHeadroom = builder.propagationHeadroom;
        this.clock = builder.clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    public synchronized HealthEngine registerDependency(String name) {
        graph.registerDependency(name);
        states.putIfAbsent(name, new DependencyState());
        return this;
    }

    public synchronized HealthEngine registerCall(String aggregator, String downstream) {
        graph.registerCall(aggregator, downstream);
        return this;
    }

    public synchronized HealthEngine registerFeature(String feature, String... directDependencies) {
        graph.registerFeature(feature, directDependencies);
        return this;
    }

    /**
     * 执行一轮评估。未传入指标的依赖沿用上一轮原始分（初始为 100）。
     */
    public synchronized EvaluationReport evaluate(Map<String, CallMetrics> metricsByDependency) {
        for (Map.Entry<String, CallMetrics> entry : metricsByDependency.entrySet()) {
            DependencyState state = states.get(entry.getKey());
            if (state == null) {
                throw new IllegalArgumentException("未登记的依赖: " + entry.getKey());
            }
            state.rawScore = scorer.score(entry.getValue());
        }

        Map<String, Double> effectiveScores = computeEffectiveScores();
        Instant now = clock.instant();
        List<AuditEvent> newEvents = new ArrayList<>();

        for (Map.Entry<String, DependencyState> entry : states.entrySet()) {
            String dependency = entry.getKey();
            DependencyState state = entry.getValue();
            double effective = effectiveScores.get(dependency);

            if (!state.degraded && effective < degradeThreshold) {
                state.degraded = true;
                state.degradedAt = now;
                state.consecutiveHealthy = 0;
                totalDegradations++;
                Set<String> impacted = graph.impactedFeatures(dependency);
                AuditEvent event = new AuditEvent(now, dependency, AuditEvent.Action.DEGRADED, effective,
                        String.format("有效分 %.2f 低于降级阈值 %.2f", effective, degradeThreshold),
                        impacted);
                auditLog.add(event);
                newEvents.add(event);
            } else if (state.degraded) {
                if (effective >= recoverThreshold) {
                    state.consecutiveHealthy++;
                    if (state.consecutiveHealthy >= consecutiveRecoveriesRequired) {
                        state.degraded = false;
                        state.consecutiveHealthy = 0;
                        recoveryDurations.add(Duration.between(state.degradedAt, now));
                        Set<String> impacted = graph.impactedFeatures(dependency);
                        AuditEvent event = new AuditEvent(now, dependency, AuditEvent.Action.RECOVERED,
                                effective,
                                String.format("有效分 %.2f 连续 %d 次评估不低于恢复阈值 %.2f",
                                        effective, consecutiveRecoveriesRequired, recoverThreshold),
                                impacted);
                        auditLog.add(event);
                        newEvents.add(event);
                    }
                } else {
                    state.consecutiveHealthy = 0;
                }
            }
        }

        Map<String, Double> rawScores = new LinkedHashMap<>();
        states.forEach((name, state) -> rawScores.put(name, state.rawScore));
        return new EvaluationReport(rawScores, effectiveScores, degradedDependencies(),
                degradedFeatures(), newEvents);
    }

    /**
     * 传播：有效分 = min(原始分, 各直接下游有效分 + headroom)。
     */
    private Map<String, Double> computeEffectiveScores() {
        Map<String, Double> memo = new HashMap<>();
        for (String dependency : states.keySet()) {
            effectiveScore(dependency, memo);
        }
        return memo;
    }

    private double effectiveScore(String dependency, Map<String, Double> memo) {
        Double cached = memo.get(dependency);
        if (cached != null) {
            return cached;
        }
        double effective = states.get(dependency).rawScore;
        for (String downstream : graph.directDownstreams(dependency)) {
            effective = Math.min(effective, effectiveScore(downstream, memo) + propagationHeadroom);
        }
        memo.put(dependency, effective);
        return effective;
    }

    public synchronized boolean isDegraded(String dependency) {
        DependencyState state = states.get(dependency);
        if (state == null) {
            throw new IllegalArgumentException("未登记的依赖: " + dependency);
        }
        return state.degraded;
    }

    public synchronized boolean isFeatureDegraded(String feature) {
        return degradedFeatures().contains(feature);
    }

    public synchronized Set<String> degradedDependencies() {
        Set<String> result = new LinkedHashSet<>();
        states.forEach((name, state) -> {
            if (state.degraded) {
                result.add(name);
            }
        });
        return result;
    }

    /**
     * 当前应降级的功能 = 所有已降级依赖影响面的并集。
     */
    public synchronized Set<String> degradedFeatures() {
        Set<String> result = new LinkedHashSet<>();
        for (String dependency : degradedDependencies()) {
            result.addAll(graph.impactedFeatures(dependency));
        }
        return result;
    }

    /**
     * 某依赖一旦降级会影响到的功能清单（与当前健康状态无关）。
     */
    public synchronized Set<String> impactedFeaturesOf(String dependency) {
        return graph.impactedFeatures(dependency);
    }

    public synchronized List<AuditEvent> auditEvents() {
        return List.copyOf(auditLog);
    }

    public synchronized HealthStats stats() {
        Duration average = Duration.ZERO;
        if (!recoveryDurations.isEmpty()) {
            long totalMillis = recoveryDurations.stream().mapToLong(Duration::toMillis).sum();
            average = Duration.ofMillis(totalMillis / recoveryDurations.size());
        }
        return new HealthStats(states.size(), degradedDependencies(), totalDegradations,
                recoveryDurations.size(), average);
    }

    private static final class DependencyState {
        private double rawScore = 100.0;
        private boolean degraded;
        private int consecutiveHealthy;
        private Instant degradedAt;
    }

    public static final class Builder {
        private HealthWeights weights = HealthWeights.of(0.5, 0.3, 0.2);
        private double goodLatencyMs = 200;
        private double badLatencyMs = 2000;
        private double degradeThreshold = 60;
        private double recoverThreshold = 80;
        private int consecutiveRecoveries = 3;
        private double propagationHeadroom = 10;
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        public Builder weights(HealthWeights weights) {
            this.weights = weights;
            return this;
        }

        public Builder latencyBands(double goodLatencyMs, double badLatencyMs) {
            this.goodLatencyMs = goodLatencyMs;
            this.badLatencyMs = badLatencyMs;
            return this;
        }

        public Builder degradeThreshold(double degradeThreshold) {
            this.degradeThreshold = degradeThreshold;
            return this;
        }

        public Builder recoverThreshold(double recoverThreshold) {
            this.recoverThreshold = recoverThreshold;
            return this;
        }

        public Builder consecutiveRecoveries(int consecutiveRecoveries) {
            this.consecutiveRecoveries = consecutiveRecoveries;
            return this;
        }

        public Builder propagationHeadroom(double propagationHeadroom) {
            this.propagationHeadroom = propagationHeadroom;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public HealthEngine build() {
            if (degradeThreshold < 0 || degradeThreshold > 100
                    || recoverThreshold < 0 || recoverThreshold > 100) {
                throw new IllegalArgumentException("阈值必须在 [0, 100] 范围内");
            }
            if (recoverThreshold < degradeThreshold) {
                throw new IllegalArgumentException("recoverThreshold 必须 >= degradeThreshold（迟滞区间）");
            }
            if (consecutiveRecoveries < 1) {
                throw new IllegalArgumentException("consecutiveRecoveries 必须 >= 1");
            }
            if (propagationHeadroom < 0) {
                throw new IllegalArgumentException("propagationHeadroom 不能为负数");
            }
            if (clock == null) {
                throw new IllegalArgumentException("clock 不能为空");
            }
            return new HealthEngine(this);
        }
    }
}
