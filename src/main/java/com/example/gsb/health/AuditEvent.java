package com.example.gsb.health;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * 一条降级/恢复决策审计记录。
 */
public final class AuditEvent {

    public enum Action {
        DEGRADED,
        RECOVERED
    }

    private final Instant timestamp;
    private final String dependency;
    private final Action action;
    private final double score;
    private final String reason;
    private final Set<String> impactedFeatures;

    public AuditEvent(Instant timestamp, String dependency, Action action, double score,
                      String reason, Set<String> impactedFeatures) {
        this.timestamp = timestamp;
        this.dependency = dependency;
        this.action = action;
        this.score = score;
        this.reason = reason;
        this.impactedFeatures = Set.copyOf(impactedFeatures);
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String dependency() {
        return dependency;
    }

    public Action action() {
        return action;
    }

    public double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public Set<String> impactedFeatures() {
        return Collections.unmodifiableSet(impactedFeatures);
    }

    @Override
    public String toString() {
        return "AuditEvent{" + timestamp + " " + action + " dependency=" + dependency
                + " score=" + score + " impacted=" + impactedFeatures + " reason='" + reason + "'}";
    }
}
