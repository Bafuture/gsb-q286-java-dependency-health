package com.example.gsb.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 统计：依赖数、当前降级项、降级触发次数、平均恢复时间。 */
class StatisticsTest {

    private HealthAssessmentEngine newEngine(MutableClock clock) {
        return HealthAssessmentEngine.builder()
                .weights(new ScoreWeights(1.0, 0.0, 0.0, 1000))
                .degradeThreshold(60)
                .requiredConsecutiveHealthyPasses(1)
                .clock(clock)
                .build();
    }

    @Test
    void reportsDependencyCountAndCurrentDegradedSet() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(clock);
        engine.addCallEdge("order-service", "payment");
        engine.registerDependency("search-engine");

        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
        engine.evaluate();

        DegradationStats stats = engine.stats();
        assertThat(stats.dependencyCount()).isEqualTo(3);
        assertThat(stats.currentlyDegraded()).containsExactlyInAnyOrder("payment", "order-service");
        assertThat(stats.degradationTriggerCount()).isEqualTo(2);
        assertThat(stats.completedRecoveryCount()).isZero();
        assertThat(stats.averageRecoveryTime()).isNull();
    }

    @Test
    void computesAverageRecoveryTimeAcrossRecoveries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(clock);
        engine.registerDependency("payment");
        engine.registerDependency("inventory");

        // payment：降级后 10 分钟恢复
        engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
        engine.evaluate();
        clock.advance(Duration.ofMinutes(10));
        engine.recordMetrics("payment", new CallMetrics(0.0, 0, 0));
        engine.evaluate();

        // inventory：降级后 20 分钟恢复
        engine.recordMetrics("inventory", new CallMetrics(0.8, 0, 0));
        engine.evaluate();
        clock.advance(Duration.ofMinutes(20));
        engine.recordMetrics("inventory", new CallMetrics(0.0, 0, 0));
        engine.evaluate();

        DegradationStats stats = engine.stats();
        assertThat(stats.degradationTriggerCount()).isEqualTo(2);
        assertThat(stats.completedRecoveryCount()).isEqualTo(2);
        assertThat(stats.averageRecoveryTime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(stats.currentlyDegraded()).isEmpty();
    }

    @Test
    void flappingCountsEachTriggerSeparately() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        HealthAssessmentEngine engine = newEngine(clock);
        engine.registerDependency("payment");

        for (int i = 0; i < 3; i++) {
            engine.recordMetrics("payment", new CallMetrics(0.8, 0, 0));
            engine.evaluate();
            engine.recordMetrics("payment", new CallMetrics(0.0, 0, 0));
            engine.evaluate();
        }

        DegradationStats stats = engine.stats();
        assertThat(stats.degradationTriggerCount()).isEqualTo(3);
        assertThat(stats.completedRecoveryCount()).isEqualTo(3);
    }
}
