package com.example.gsb.health;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * 组件统计快照。
 */
public final class HealthStats {

    private final int dependencyCount;
    private final Set<String> currentDegradedDependencies;
    private final long totalDegradationCount;
    private final long completedRecoveryCount;
    private final Duration averageRecoveryTime;

    HealthStats(int dependencyCount,
                Set<String> currentDegradedDependencies,
                long totalDegradationCount,
                long completedRecoveryCount,
                Duration averageRecoveryTime) {
        this.dependencyCount = dependencyCount;
        this.currentDegradedDependencies = Set.copyOf(currentDegradedDependencies);
        this.totalDegradationCount = totalDegradationCount;
        this.completedRecoveryCount = completedRecoveryCount;
        this.averageRecoveryTime = averageRecoveryTime;
    }

    public int dependencyCount() {
        return dependencyCount;
    }

    public Set<String> currentDegradedDependencies() {
        return Collections.unmodifiableSet(currentDegradedDependencies);
    }

    public long totalDegradationCount() {
        return totalDegradationCount;
    }

    public long completedRecoveryCount() {
        return completedRecoveryCount;
    }

    /**
     * 历次“降级 -> 恢复”的平均耗时；尚未发生过恢复时为 {@link Duration#ZERO}。
     */
    public Duration averageRecoveryTime() {
        return averageRecoveryTime;
    }
}
