package com.example.gsb.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务依赖健康评估与降级决策引擎。
 *
 * <p>传播规则（最弱链路规则）：一个依赖的有效健康分 = min(自身健康分, 其所有直接下游的有效健康分)。
 * 即聚合服务的健康度不超过其最弱的下游；沿调用边递归计算，对环安全。
 *
 * <p>降级判定：有效健康分 &lt; degradeThreshold 时进入降级，只降级该依赖（传递）影响到的功能。
 *
 * <p>恢复判定（抖动抑制）：处于降级状态的依赖，需连续 requiredConsecutiveHealthyPasses 次评估
 * 有效分 &ge; recoveryThreshold 才解除降级；中途任何一次不达标则计数清零。
 */
public class HealthAssessmentEngine {

    private final DependencyGraph graph = new DependencyGraph();
    private final HealthScorer scorer;
    private final double degradeThreshold;
    private final double recoveryThreshold;
    private final int requiredConsecutiveHealthyPasses;
    private final Clock clock;
    private final AuditTrail auditTrail = new AuditTrail();

    private final Map<String, CallMetrics> latestMetrics = new LinkedHashMap<>();
    private final Map<String, DependencyState> states = new LinkedHashMap<>();

    private HealthAssessmentEngine(Builder builder) {
        this.scorer = new HealthScorer(builder.weights);
        this.degradeThreshold = builder.degradeThreshold;
        this.recoveryThreshold = builder.recoveryThreshold;
        this.requiredConsecutiveHealthyPasses = builder.requiredConsecutiveHealthyPasses;
        this.clock = builder.clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DependencyGraph graph() {
        return graph;
    }

    public void registerDependency(String name) {
        graph.registerDependency(name);
    }

    public void addCallEdge(String upstream, String downstream) {
        graph.addCallEdge(upstream, downstream);
    }

    public void mapFeature(String feature, String dependency) {
        graph.mapFeature(feature, dependency);
    }

    /** 上报某依赖在当前窗口的调用指标。 */
    public void recordMetrics(String dependency, CallMetrics metrics) {
        graph.registerDependency(dependency);
        latestMetrics.put(dependency, metrics);
    }

    public AuditTrail auditTrail() {
        return auditTrail;
    }

    /** 执行一次全量评估：评分 → 传播 → 降级/恢复判定 → 审计。 */
    public EvaluationReport evaluate() {
        Instant now = clock.instant();
        Map<String, Double> ownScores = new LinkedHashMap<>();
        for (String dep : graph.dependencies()) {
            ownScores.put(dep, scorer.score(latestMetrics.getOrDefault(dep, CallMetrics.noData())));
        }
        Map<String, Double> effectiveScores = new LinkedHashMap<>();
        for (String dep : graph.dependencies()) {
            effectiveScores.put(dep, effectiveScore(dep, ownScores, effectiveScores, new LinkedHashSet<>()));
        }

        List<AuditEvent> newEvents = new ArrayList<>();
        for (String dep : graph.dependencies()) {
            DependencyState state = states.computeIfAbsent(dep, k -> new DependencyState());
            double effective = effectiveScores.get(dep);
            if (effective < degradeThreshold) {
                state.consecutiveHealthyPasses = 0;
                if (!state.degraded) {
                    state.degraded = true;
                    state.degradedAt = now;
                    Set<String> impacted = graph.impactedFeatures(dep);
                    AuditEvent event = new AuditEvent(now, AuditEvent.Type.DEGRADED, dep,
                            ownScores.get(dep), effective,
                            "effective score %.2f < degrade threshold %.2f".formatted(effective, degradeThreshold),
                            impacted);
                    auditTrail.recordDegradation(event);
                    newEvents.add(event);
                }
            } else if (effective >= recoveryThreshold) {
                if (state.degraded) {
                    state.consecutiveHealthyPasses++;
                    if (state.consecutiveHealthyPasses >= requiredConsecutiveHealthyPasses) {
                        state.degraded = false;
                        Duration recoveryTime = Duration.between(state.degradedAt, now);
                        state.degradedAt = null;
                        Set<String> impacted = graph.impactedFeatures(dep);
                        AuditEvent event = new AuditEvent(now, AuditEvent.Type.RECOVERED, dep,
                                ownScores.get(dep), effective,
                                "effective score %.2f >= recovery threshold %.2f for %d consecutive pass(es)"
                                        .formatted(effective, recoveryThreshold, state.consecutiveHealthyPasses),
                                impacted);
                        auditTrail.recordRecovery(event, recoveryTime);
                        newEvents.add(event);
                        state.consecutiveHealthyPasses = 0;
                    }
                }
            } else {
                // 位于 [degradeThreshold, recoveryThreshold) 迟滞区间：保持降级且不计入连续恢复次数。
                state.consecutiveHealthyPasses = 0;
            }
        }

        Set<String> degradedDeps = degradedDependencies();
        Set<String> degradedFeatures = new LinkedHashSet<>();
        Map<String, Set<String>> featureCauses = new LinkedHashMap<>();
        for (String dep : degradedDeps) {
            for (String feature : graph.impactedFeatures(dep)) {
                degradedFeatures.add(feature);
                featureCauses.computeIfAbsent(feature, k -> new LinkedHashSet<>()).add(dep);
            }
        }
        return new EvaluationReport(ownScores, effectiveScores, degradedDeps, degradedFeatures,
                featureCauses, newEvents);
    }

    /** 传播计算：有效分 = min(自身分, 所有直接下游的有效分)。 */
    private double effectiveScore(String dep, Map<String, Double> ownScores,
                                  Map<String, Double> memo, Set<String> visiting) {
        Double cached = memo.get(dep);
        if (cached != null) {
            return cached;
        }
        double score = ownScores.get(dep);
        if (visiting.add(dep)) {
            for (String downstream : graph.directDownstreamsOf(dep)) {
                score = Math.min(score, effectiveScore(downstream, ownScores, memo, visiting));
            }
            visiting.remove(dep);
        }
        memo.put(dep, score);
        return score;
    }

    public Set<String> degradedDependencies() {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, DependencyState> entry : states.entrySet()) {
            if (entry.getValue().degraded) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** 当前统计快照：依赖数、当前降级项、累计降级触发次数、平均恢复时间。 */
    public DegradationStats stats() {
        List<Duration> recoveryTimes = auditTrail.recoveryTimes();
        Duration average = null;
        if (!recoveryTimes.isEmpty()) {
            long totalMillis = recoveryTimes.stream().mapToLong(Duration::toMillis).sum();
            average = Duration.ofMillis(totalMillis / recoveryTimes.size());
        }
        return new DegradationStats(graph.dependencies().size(), degradedDependencies(),
                auditTrail.degradationTriggerCount(), recoveryTimes.size(), average);
    }

    private static final class DependencyState {
        boolean degraded;
        int consecutiveHealthyPasses;
        Instant degradedAt;
    }

    public static final class Builder {
        private ScoreWeights weights = ScoreWeights.defaults();
        private double degradeThreshold = 60;
        private double recoveryThreshold = 60;
        private int requiredConsecutiveHealthyPasses = 3;
        private Clock clock = Clock.systemUTC();

        public Builder weights(ScoreWeights weights) {
            this.weights = weights;
            return this;
        }

        public Builder degradeThreshold(double degradeThreshold) {
            this.degradeThreshold = degradeThreshold;
            return this;
        }

        /** 恢复阈值，应不低于降级阈值（形成迟滞区间，进一步抑制抖动）。 */
        public Builder recoveryThreshold(double recoveryThreshold) {
            this.recoveryThreshold = recoveryThreshold;
            return this;
        }

        public Builder requiredConsecutiveHealthyPasses(int passes) {
            this.requiredConsecutiveHealthyPasses = passes;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public HealthAssessmentEngine build() {
            if (requiredConsecutiveHealthyPasses < 1) {
                throw new IllegalArgumentException("requiredConsecutiveHealthyPasses must be >= 1");
            }
            if (recoveryThreshold < degradeThreshold) {
                throw new IllegalArgumentException("recoveryThreshold must be >= degradeThreshold");
            }
            return new HealthAssessmentEngine(this);
        }
    }
}
