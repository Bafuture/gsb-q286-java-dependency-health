package com.example.gsb.health;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次评估的结果：各依赖的原始分/有效分、当前降级的依赖与功能，
 * 以及本轮新产生的降级/恢复审计事件。
 */
public final class EvaluationReport {

    private final Map<String, Double> rawScores;
    private final Map<String, Double> effectiveScores;
    private final Set<String> degradedDependencies;
    private final Set<String> degradedFeatures;
    private final List<AuditEvent> events;

    EvaluationReport(Map<String, Double> rawScores,
                     Map<String, Double> effectiveScores,
                     Set<String> degradedDependencies,
                     Set<String> degradedFeatures,
                     List<AuditEvent> events) {
        this.rawScores = Map.copyOf(rawScores);
        this.effectiveScores = Map.copyOf(effectiveScores);
        this.degradedDependencies = Set.copyOf(degradedDependencies);
        this.degradedFeatures = Set.copyOf(degradedFeatures);
        this.events = List.copyOf(events);
    }

    public Map<String, Double> rawScores() {
        return Collections.unmodifiableMap(rawScores);
    }

    public Map<String, Double> effectiveScores() {
        return Collections.unmodifiableMap(effectiveScores);
    }

    public Set<String> degradedDependencies() {
        return Collections.unmodifiableSet(degradedDependencies);
    }

    public Set<String> degradedFeatures() {
        return Collections.unmodifiableSet(degradedFeatures);
    }

    public List<AuditEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
